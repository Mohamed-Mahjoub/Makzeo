#!/usr/bin/env python3
"""
Validation hors-ligne des manifests (sans cluster) :
  - rend les overlays Kustomize (sous-ensemble utilisé par le projet : resources, namespace,
    labels, images, secretGenerator, patches JSON6902) ;
  - vérifie la cohérence Kubernetes : sélecteurs, ports nommés, clés de ConfigMap/Secret,
    backends d'Ingress, cibles des HPA / PDB ;
  - vérifie la cohérence Tekton : tâches, paramètres, workspaces, résultats, runAfter, triggers ;
  - analyse la syntaxe YAML des manifests FluxCD.
Usage : python scripts/validate_manifests.py    (nécessite PyYAML : pip install pyyaml)
"""
import copy
import os
import re
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors = []
checks = 0


def err(msg):
    errors.append(msg)


def ok():
    global checks
    checks += 1


def load_docs(path):
    with open(path, encoding="utf-8") as f:
        return [d for d in yaml.safe_load_all(f) if d]


# ---------------------------------------------------------------- mini-kustomize
def json_pointer(path):
    return [p.replace("~1", "/").replace("~0", "~") for p in path.lstrip("/").split("/")]


def apply_json_patch(doc, ops):
    for op in ops:
        parts = json_pointer(op["path"])
        parent = doc
        for p in parts[:-1]:
            parent = parent[int(p)] if isinstance(parent, list) else parent[p]
        last = parts[-1]
        if op["op"] in ("add", "replace"):
            if isinstance(parent, list):
                if last == "-":
                    parent.append(op["value"])
                elif op["op"] == "add":
                    parent.insert(int(last), op["value"])
                else:
                    parent[int(last)] = op["value"]
            else:
                if op["op"] == "replace" and last not in parent:
                    raise KeyError(f"replace sur un chemin inexistant {op['path']}")
                parent[last] = op["value"]
        elif op["op"] == "remove":
            del parent[int(last) if isinstance(parent, list) else last]
        else:
            raise ValueError("op non supportée " + op["op"])


def match_selector(labels, selector):
    for term in [t.strip() for t in re.split(r",(?![^()]*\))", selector) if t.strip()]:
        m = re.match(r"([\w./-]+)\s+in\s+\(([^)]*)\)", term)
        if m:
            if labels.get(m.group(1)) not in [v.strip() for v in m.group(2).split(",")]:
                return False
        else:
            k, v = term.split("=", 1)
            if labels.get(k.strip()) != v.strip():
                return False
    return True


def build(directory):
    kfile = os.path.join(directory, "kustomization.yaml")
    k = load_docs(kfile)[0]
    out = []
    for res in k.get("resources", []):
        p = os.path.normpath(os.path.join(directory, res))
        if os.path.isdir(p):
            out.extend(build(p))
        elif os.path.isfile(p):
            out.extend(load_docs(p))
        else:
            err(f"{kfile}: ressource introuvable {res}")
    for gen in k.get("secretGenerator", []):
        data = dict(l.split("=", 1) for l in gen.get("literals", []))
        out.append({"apiVersion": "v1", "kind": "Secret", "metadata": {"name": gen["name"]}, "stringData": data})
    ns = k.get("namespace")
    for d in out:
        if ns and d["kind"] not in ("Namespace", "ClusterRole", "ClusterRoleBinding"):
            d["metadata"]["namespace"] = ns
        for lab in k.get("labels", []):
            d["metadata"].setdefault("labels", {}).update(lab["pairs"])
            if lab.get("includeSelectors"):
                raise ValueError("includeSelectors non géré")
    for img in k.get("images", []):
        found = False
        for d in out:
            for c in pod_containers(d):
                name, _, tag = c["image"].partition(":")
                if name == img["name"]:
                    found = True
                    c["image"] = f"{img.get('newName', name)}:{img.get('newTag', tag)}"
        if not found:
            err(f"{kfile}: image {img['name']} ne correspond à aucun conteneur")
    for patch in k.get("patches", []):
        t = patch["target"]
        targets = [d for d in out if d["kind"] == t.get("kind", d["kind"])
                   and d["metadata"]["name"] == t.get("name", d["metadata"]["name"])
                   and (not t.get("labelSelector") or match_selector(d["metadata"].get("labels", {}), t["labelSelector"]))]
        if not targets:
            err(f"{kfile}: patch sans cible {t}")
        for d in targets:
            try:
                apply_json_patch(d, yaml.safe_load(patch["patch"]))
            except Exception as e:  # noqa
                err(f"{kfile}: patch {t} invalide : {e}")
    return copy.deepcopy(out)


def pod_spec(d):
    if d["kind"] in ("Deployment", "StatefulSet"):
        return d["spec"]["template"]["spec"]
    return None


def pod_containers(d):
    spec = pod_spec(d)
    return spec["containers"] if spec else []


# ---------------------------------------------------------------- contrôles Kubernetes
def check_k8s(docs, label):
    by = lambda kind: {d["metadata"]["name"]: d for d in docs if d["kind"] == kind}
    names = set()
    for d in docs:
        for f in ("apiVersion", "kind"):
            if f not in d:
                err(f"[{label}] champ {f} manquant")
        key = (d["kind"], d["metadata"].get("namespace"), d["metadata"]["name"])
        if key in names:
            err(f"[{label}] ressource en double {key}")
        names.add(key)
        ok()
    configmaps, secrets = by("ConfigMap"), by("Secret")
    workloads = list(by("Deployment").values()) + list(by("StatefulSet").values())
    for w in workloads:
        name = w["metadata"]["name"]
        sel = w["spec"]["selector"]["matchLabels"]
        tl = w["spec"]["template"]["metadata"]["labels"]
        if any(tl.get(k) != v for k, v in sel.items()):
            err(f"[{label}] {name}: selector ne correspond pas aux labels du template")
        ok()
        if "replicas" in w["spec"] and w["kind"] == "Deployment" and by("HorizontalPodAutoscaler").get(name):
            err(f"[{label}] {name}: replicas fixé alors qu'un HPA le pilote")
        vols = {v["name"] for v in pod_spec(w).get("volumes", [])} | \
               {v["metadata"]["name"] for v in w["spec"].get("volumeClaimTemplates", [])}
        for c in pod_containers(w):
            if ":" not in c["image"]:
                err(f"[{label}] {name}: image sans tag")
            ports = {p.get("name") for p in c.get("ports", [])} | {p["containerPort"] for p in c.get("ports", [])}
            for probe in ("startupProbe", "livenessProbe", "readinessProbe"):
                pr = c.get(probe, {})
                port = pr.get("httpGet", {}).get("port")
                if port is not None and port not in ports:
                    err(f"[{label}] {name}: {probe} vers un port inconnu {port}")
                ok()
            for e in c.get("env", []):
                ref = e.get("valueFrom", {})
                for kind, store in (("configMapKeyRef", configmaps), ("secretKeyRef", secrets)):
                    if kind in ref:
                        r = ref[kind]
                        src = store.get(r["name"])
                        if src is None:
                            if not r.get("optional"):
                                err(f"[{label}] {name}: {kind} {r['name']} introuvable")
                        else:
                            data = {**src.get("data", {}), **src.get("stringData", {})}
                            if r["key"] not in data:
                                err(f"[{label}] {name}: clé {r['key']} absente de {r['name']}")
                        ok()
            for vm in c.get("volumeMounts", []):
                if vm["name"] not in vols:
                    err(f"[{label}] {name}: volumeMount {vm['name']} sans volume")
                ok()
    services = by("Service")
    for s in services.values():
        targets = [w for w in workloads if all(w["spec"]["template"]["metadata"]["labels"].get(k) == v
                                               for k, v in s["spec"]["selector"].items())]
        if not targets:
            err(f"[{label}] Service {s['metadata']['name']} ne sélectionne aucun pod")
        for p in s["spec"]["ports"]:
            tp = p.get("targetPort", p["port"])
            for w in targets:
                pod_ports = {pp.get("name") for c in pod_containers(w) for pp in c.get("ports", [])} | \
                            {pp["containerPort"] for c in pod_containers(w) for pp in c.get("ports", [])}
                if tp not in pod_ports:
                    err(f"[{label}] Service {s['metadata']['name']}: targetPort {tp} absent du pod")
        ok()
    for ing in by("Ingress").values():
        for rule in ing["spec"]["rules"]:
            for path in rule["http"]["paths"]:
                b = path["backend"]["service"]
                svc = services.get(b["name"])
                if not svc:
                    err(f"[{label}] Ingress: Service {b['name']} introuvable")
                elif b["port"]["number"] not in [p["port"] for p in svc["spec"]["ports"]]:
                    err(f"[{label}] Ingress: port {b['port']['number']} absent du Service {b['name']}")
                ok()
    deployments = by("Deployment")
    for h in by("HorizontalPodAutoscaler").values():
        if h["spec"]["scaleTargetRef"]["name"] not in deployments:
            err(f"[{label}] HPA {h['metadata']['name']}: cible introuvable")
        ok()
    for p in by("PodDisruptionBudget").values():
        if not any(all(w["spec"]["template"]["metadata"]["labels"].get(k) == v
                       for k, v in p["spec"]["selector"]["matchLabels"].items()) for w in workloads):
            err(f"[{label}] PDB {p['metadata']['name']}: aucun pod ciblé")
        ok()
    for np_ in by("NetworkPolicy").values():
        sel = np_["spec"]["podSelector"]["matchLabels"]
        if not any(all(w["spec"]["template"]["metadata"]["labels"].get(k) == v for k, v in sel.items()) for w in workloads):
            err(f"[{label}] NetworkPolicy {np_['metadata']['name']}: aucun pod ciblé")
        ok()


# ---------------------------------------------------------------- contrôles Tekton
def check_tekton(docs):
    tasks = {d["metadata"]["name"]: d for d in docs if d["kind"] == "Task"}
    pipelines = {d["metadata"]["name"]: d for d in docs if d["kind"] == "Pipeline"}
    for t in tasks.values():
        declared = {p["name"] for p in t["spec"].get("params", [])}
        wss = {w["name"] for w in t["spec"].get("workspaces", [])}
        res = {r["name"] for r in t["spec"].get("results", [])}
        text = yaml.safe_dump(t["spec"])
        for p in re.findall(r"\$\(params\.([\w-]+)\)", text):
            if p not in declared:
                err(f"Task {t['metadata']['name']}: paramètre non déclaré {p}")
        for w in re.findall(r"\$\(workspaces\.([\w-]+)\.", text):
            if w not in wss:
                err(f"Task {t['metadata']['name']}: workspace non déclaré {w}")
        for r in re.findall(r"\$\(results\.([\w-]+)\.path\)", text):
            if r not in res:
                err(f"Task {t['metadata']['name']}: résultat non déclaré {r}")
        ok()
    for pl in pipelines.values():
        pparams = {p["name"] for p in pl["spec"]["params"]}
        pws = {w["name"] for w in pl["spec"]["workspaces"]}
        names = {t["name"] for t in pl["spec"]["tasks"]}
        for pt in pl["spec"]["tasks"]:
            task = tasks.get(pt["taskRef"]["name"])
            if not task:
                err(f"Pipeline: tâche {pt['taskRef']['name']} introuvable")
                continue
            tparams = {p["name"]: p for p in task["spec"].get("params", [])}
            given = {p["name"] for p in pt.get("params", [])}
            for g in given - set(tparams):
                err(f"Pipeline/{pt['name']}: paramètre inconnu {g}")
            for n, p in tparams.items():
                if "default" not in p and n not in given:
                    err(f"Pipeline/{pt['name']}: paramètre obligatoire {n} non fourni")
            twss = {w["name"]: w for w in task["spec"].get("workspaces", [])}
            bound = {w["name"] for w in pt.get("workspaces", [])}
            for w in pt.get("workspaces", []):
                if w["name"] not in twss:
                    err(f"Pipeline/{pt['name']}: workspace inconnu {w['name']}")
                if w["workspace"] not in pws:
                    err(f"Pipeline/{pt['name']}: workspace pipeline inconnu {w['workspace']}")
            for n, w in twss.items():
                if not w.get("optional") and n not in bound:
                    err(f"Pipeline/{pt['name']}: workspace obligatoire {n} non lié")
            for r in pt.get("runAfter", []):
                if r not in names:
                    err(f"Pipeline/{pt['name']}: runAfter {r} inconnu")
            text = yaml.safe_dump(pt)
            for p in re.findall(r"\$\(params\.([\w-]+)\)", text):
                if p not in pparams:
                    err(f"Pipeline/{pt['name']}: paramètre pipeline inconnu {p}")
            for tname, rname in re.findall(r"\$\(tasks\.([\w-]+)\.results\.([\w-]+)\)", text):
                src = next((t for t in pl["spec"]["tasks"] if t["name"] == tname), None)
                if not src or rname not in {r["name"] for r in tasks[src["taskRef"]["name"]]["spec"].get("results", [])}:
                    err(f"Pipeline/{pt['name']}: résultat {tname}.{rname} introuvable")
            ok()
    tts = {d["metadata"]["name"]: d for d in docs if d["kind"] == "TriggerTemplate"}
    tbs = {d["metadata"]["name"]: d for d in docs if d["kind"] == "TriggerBinding"}
    for tt in tts.values():
        declared = {p["name"] for p in tt["spec"]["params"]}
        for p in re.findall(r"\$\(tt\.params\.([\w-]+)\)", yaml.safe_dump(tt["spec"])):
            if p not in declared:
                err(f"TriggerTemplate: paramètre {p} non déclaré")
        for rt in tt["spec"]["resourcetemplates"]:
            pl = pipelines.get(rt["spec"]["pipelineRef"]["name"])
            if not pl:
                err("TriggerTemplate: pipeline introuvable")
                continue
            given = {p["name"] for p in rt["spec"]["params"]}
            for p in pl["spec"]["params"]:
                if "default" not in p and p["name"] not in given:
                    err(f"TriggerTemplate: paramètre pipeline {p['name']} non fourni")
            wss = {w["name"] for w in rt["spec"]["workspaces"]}
            for w in pl["spec"]["workspaces"]:
                if not w.get("optional") and w["name"] not in wss:
                    err(f"TriggerTemplate: workspace {w['name']} non fourni")
        ok()
    for el in [d for d in docs if d["kind"] == "EventListener"]:
        for trg in el["spec"]["triggers"]:
            if trg["template"]["ref"] not in tts:
                err("EventListener: template introuvable")
            for b in trg["bindings"]:
                binding = tbs.get(b["ref"])
                if not binding:
                    err("EventListener: binding introuvable")
                    continue
                tt = tts[trg["template"]["ref"]]
                ttp = {p["name"]: p for p in tt["spec"]["params"]}
                for p in binding["spec"]["params"]:
                    if p["name"] not in ttp:
                        err(f"TriggerBinding: paramètre {p['name']} absent du template")
                missing = [n for n, p in ttp.items() if "default" not in p and
                           n not in {q["name"] for q in binding["spec"]["params"]}]
                if missing:
                    err(f"TriggerTemplate: paramètres sans valeur {missing}")
            ok()


def main():
    for overlay in ("dev", "prod"):
        docs = build(os.path.join(ROOT, "deploy/k8s/overlays", overlay))
        check_k8s(docs, overlay)
        print(f"overlay {overlay:4s} : {len(docs)} ressources rendues")
    tekton = build(os.path.join(ROOT, "deploy/tekton"))
    tekton += load_docs(os.path.join(ROOT, "deploy/tekton/pipelinerun.yaml"))
    check_tekton(tekton)
    print(f"tekton      : {len(tekton)} ressources")
    flux = build(os.path.join(ROOT, "deploy/flux")) + load_docs(os.path.join(ROOT, "deploy/flux/notifications.yaml"))
    for d in flux:
        if d["kind"] == "Kustomization" and not os.path.isfile(os.path.join(ROOT, d["spec"]["path"], "kustomization.yaml")):
            err(f"Flux {d['metadata']['name']}: chemin {d['spec']['path']} sans kustomization.yaml")
        ok()
    print(f"flux        : {len(flux)} ressources")
    if errors:
        print(f"\n{len(errors)} ERREUR(S) :")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print(f"\nOK : {checks} vérifications, aucune erreur.")


if __name__ == "__main__":
    main()
