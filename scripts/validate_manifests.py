#!/usr/bin/env python3
"""
Validation hors-ligne des manifests (sans cluster), exécutée par le pipeline :
  - rend l'overlay Kustomize onprem (kustomize ou kubectl kustomize, rendu interne à défaut) ;
  - vérifie la cohérence Kubernetes : sélecteurs, ports nommés, clés de ConfigMap/Secret,
    backends d'Ingress, cibles des HPA / PDB / NetworkPolicy ;
  - vérifie la supervision : ServiceMonitor, règles d'alerte, tableau de bord Grafana ;
  - vérifie la structure du pipeline azure-pipelines.yml (dépendances entre stages).
Usage : python3 scripts/validate_manifests.py    (nécessite PyYAML)
"""
import copy
import json
import os
import re
import shutil
import subprocess
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Secrets créés par le pipeline au moment du déploiement (jamais stockés dans Git)
EXTERNAL_SECRETS = {
    "stock-secrets": {"POSTGRES_USER": "", "POSTGRES_PASSWORD": "", "JWT_SECRET": "", "ADMIN_PASSWORD": ""},
    "nexus-registry": {".dockerconfigjson": ""},
}
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
        for env_file in gen.get("envs", []):
            # secrets.env n'est pas versionné : on contrôle les clés avec le modèle secrets.env.example
            env_path = os.path.join(directory, env_file)
            if not os.path.isfile(env_path) and os.path.isfile(env_path + ".example"):
                env_path += ".example"
            if not os.path.isfile(env_path):
                err(f"{kfile}: fichier de secrets introuvable {env_file} (ni {env_file}.example)")
                continue
            with open(env_path, encoding="utf-8") as fh:
                for line in fh:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        key, value = line.split("=", 1)
                        data[key.strip()] = value.strip()
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


def build_with_kustomize(directory):
    """Rend l'overlay avec kustomize (ou kubectl kustomize) ; repli sur le rendu interne sinon."""
    for cmd in (["kustomize", "build", directory], ["kubectl", "kustomize", directory]):
        if shutil.which(cmd[0]):
            out = subprocess.run(cmd, check=True, capture_output=True, text=True).stdout
            return [d for d in yaml.safe_load_all(out) if d]
    return build(directory)


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
    for name, keys in EXTERNAL_SECRETS.items():
        secrets.setdefault(name, {"stringData": keys})
    for w in [d for d in docs if d["kind"] in ("Deployment", "StatefulSet")]:
        for ps in pod_spec(w).get("imagePullSecrets", []):
            if ps["name"] not in secrets:
                err(f"[{label}] {w['metadata']['name']}: imagePullSecret {ps['name']} inconnu")
            ok()
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
                            if r["key"] not in data and not r.get("optional"):
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


# ---------------------------------------------------------------- contrôles supervision
def check_monitoring(docs, label):
    services = [d for d in docs if d["kind"] == "Service"]
    for sm in (d for d in docs if d["kind"] == "ServiceMonitor"):
        sel = sm["spec"]["selector"]["matchLabels"]
        targets = [s for s in services if all(s["metadata"].get("labels", {}).get(k) == v for k, v in sel.items())]
        if not targets:
            err(f"[{label}] ServiceMonitor {sm['metadata']['name']}: aucun Service sélectionné")
        for ep in sm["spec"]["endpoints"]:
            for s in targets:
                if ep["port"] not in [p.get("name") for p in s["spec"]["ports"]]:
                    err(f"[{label}] ServiceMonitor: port {ep['port']} absent du Service {s['metadata']['name']}")
                ok()
        ok()
    for rule in (d for d in docs if d["kind"] == "PrometheusRule"):
        for group in rule["spec"]["groups"]:
            for r in group["rules"]:
                for field in ("alert", "expr", "labels", "annotations"):
                    if field not in r:
                        err(f"[{label}] PrometheusRule {group['name']}: champ {field} manquant")
                if r.get("expr", "").count("(") != r.get("expr", "").count(")"):
                    err(f"[{label}] PrometheusRule {r.get('alert')}: parenthèses déséquilibrées")
                ok()
    for cm in (d for d in docs if d["kind"] == "ConfigMap" and d["metadata"].get("labels", {}).get("grafana_dashboard") == "1"):
        for name, content in cm.get("data", {}).items():
            try:
                dash = json.loads(content)
                if not dash.get("panels"):
                    err(f"[{label}] tableau de bord {name}: aucun panneau")
            except ValueError as e:
                err(f"[{label}] tableau de bord {name}: JSON invalide ({e})")
            ok()


# ---------------------------------------------------------------- contrôles du pipeline
def check_pipeline(path):
    with open(path, encoding="utf-8") as f:
        pipeline = yaml.safe_load(f)
    names = [s["stage"] for s in pipeline.get("stages", [])]
    for stage in pipeline.get("stages", []):
        for dep in ([stage["dependsOn"]] if isinstance(stage.get("dependsOn"), str) else stage.get("dependsOn", [])):
            if dep not in names:
                err(f"[pipeline] stage {stage['stage']}: dépendance inconnue {dep}")
            ok()
        if not stage.get("jobs"):
            err(f"[pipeline] stage {stage['stage']}: aucun job")
        ok()
    return names


def main():
    docs = build_with_kustomize(os.path.join(ROOT, "deploy/k8s/overlays/onprem"))
    check_k8s(docs, "onprem")
    check_monitoring(docs, "onprem")
    print(f"overlay onprem : {len(docs)} ressources rendues")
    stages = check_pipeline(os.path.join(ROOT, "azure-pipelines.yml"))
    print(f"pipeline       : {len(stages)} stages ({', '.join(stages)})")
    if errors:
        print(f"\n{len(errors)} erreur(s) :")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    print(f"\nOK : {checks} contrôles réussis")


if __name__ == "__main__":
    main()
