# DevOps — Docker, Kubernetes, Tekton, FluxCD

Ce dossier contient tout ce qui permet de **construire, tester, livrer et déployer**
l'application de manière automatisée.

```
deploy/
├── docker/            PostgreSQL (init) + passerelle Nginx pour docker compose
├── k8s/
│   ├── base/          manifests communs (Kustomize) : namespace, ConfigMap, PostgreSQL, 5 microservices,
│   │                  frontend, Ingress (API Gateway), NetworkPolicy
│   └── overlays/
│       ├── dev/       démo locale (minikube) : images locales, secrets de démonstration
│       └── prod/      production : registre d'images, TLS, HPA, PDB, secret chiffré SOPS
├── tekton/            CI/CD : tâches, pipeline, déclencheurs (webhook GitHub)
├── flux/              GitOps : source Git + synchronisation continue du cluster
└── scripts/           build des images en local (bash / PowerShell)
```

## Chaîne complète

```
 Développeur ──git push──► GitHub (monorepo : backend, frontend, deploy)
                               │ webhook
                               ▼
                Tekton EventListener (filtre : main, pas de « [skip ci] »)
                               ▼
      ┌──────────── Pipeline stock-ci-cd (namespace stock-ci) ─────────────┐
      │ git-clone ─┬─ maven-verify (84 tests) ─ kaniko ×5 (microservices) ─┐│
      │            └─ frontend-build (ng build) ─ kaniko (frontend) ───────┤│
      │                                                  update-gitops ◄───┘│
      └──── images poussées : registre/stock-<service>:<sha court> ─────────┘
                               │ commit « chore(deploy): images <sha> [skip ci] »
                               ▼
               GitHub : deploy/k8s/overlays/prod/kustomization.yaml (newTag)
                               │ FluxCD surveille le dépôt (1 min)
                               ▼
         Flux Kustomization ──► kubectl apply (prune + santé) ──► rolling update
```

- **CI** (Tekton) : chaque push compile, teste, construit et publie les images.
- **CD GitOps** (Flux) : Git est la source de vérité. Le pipeline ne déploie jamais directement,
  il modifie Git et Flux aligne le cluster sur Git.

---

## Partie 1 — Déployer sur Kubernetes en local (minikube)

### 1.1 Prérequis (Windows)
- Docker Desktop
- `kubectl` et `minikube` : `winget install Kubernetes.kubectl Kubernetes.minikube`

### 1.2 Démarrer le cluster
```powershell
minikube start --cpus 4 --memory 8192 --driver=docker
minikube addons enable ingress
minikube addons enable metrics-server
```

### 1.3 Construire les images dans minikube
```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
.\deploy\scripts\build-images.ps1
```
Sous Linux/macOS : `eval $(minikube docker-env) && ./deploy/scripts/build-images.sh`.

### 1.4 Déployer (overlay dev)
```powershell
kubectl apply -k deploy/k8s/overlays/dev
kubectl -n stock-management get pods -w     # attendre que les 7 pods soient Running / READY 1/1
```
Au premier démarrage, PostgreSQL crée les 4 bases, puis chaque service crée ses tables (Flyway).

### 1.5 Accéder à l'application
1. Dans un terminal **administrateur** laissé ouvert : `minikube tunnel`
2. Ajouter dans `C:\Windows\System32\drivers\etc\hosts` : `127.0.0.1 stock.local`
3. Ouvrir http://stock.local et se connecter avec `admin` / `Admin@12345`

### 1.6 Données de démonstration (optionnel)
```powershell
python backend/scripts/seed_demo_data.py --gateway http://stock.local `
  --sql-cmd "kubectl -n stock-management exec -i postgres-0 -- psql -U postgres -d stock_db"
```

### 1.7 Commandes utiles
```powershell
kubectl -n stock-management get all
kubectl -n stock-management logs deploy/stock-service -f
kubectl -n stock-management describe pod <pod>
kubectl -n stock-management rollout restart deploy/product-service
kubectl -n stock-management port-forward svc/postgres 5432:5432   # accès pgAdmin
kubectl delete -k deploy/k8s/overlays/dev                          # tout supprimer
```

---

## Partie 2 — Intégration continue avec Tekton

### 2.1 Installer Tekton
```powershell
kubectl apply -f https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml
kubectl apply -f https://storage.googleapis.com/tekton-releases/triggers/latest/release.yaml
kubectl apply -f https://storage.googleapis.com/tekton-releases/triggers/latest/interceptors.yaml
kubectl apply -f https://storage.googleapis.com/tekton-releases/dashboard/latest/release.yaml
kubectl -n tekton-pipelines get pods     # attendre que tout soit Running
```
CLI optionnelle : `winget install TektonCD.Cli` (commande `tkn`).

### 2.2 Préparer le dépôt Git et le registre
1. Pousser le monorepo sur GitHub (ex. `https://github.com/<compte>/stock-management`).
2. Remplacer `CHANGE_ME` par votre compte dans :
   `deploy/tekton/triggers.yaml`, `deploy/tekton/pipelinerun.yaml`,
   `deploy/k8s/overlays/prod/kustomization.yaml`, `deploy/flux/gitrepository.yaml`.
3. Créer un **token GitHub** (droits `repo` + `write:packages`) : il sert à pousser les images
   sur `ghcr.io` et le commit GitOps.

### 2.3 Créer le namespace, les droits et les secrets
```powershell
kubectl apply -k deploy/tekton        # namespace stock-ci, tâches, pipeline, triggers
kubectl -n stock-ci create secret docker-registry registry-credentials `
  --docker-server=ghcr.io --docker-username=<compte> --docker-password=<token>
kubectl -n stock-ci create secret generic git-credentials `
  --from-literal=username=<compte> --from-literal=token=<token>
kubectl -n stock-ci create secret generic github-webhook-secret `
  --from-literal=secretToken=<chaine-aleatoire>
```

### 2.4 Lancer le pipeline manuellement
```powershell
kubectl create -f deploy/tekton/pipelinerun.yaml
tkn pipelinerun logs -f -n stock-ci --last
```
Tableau de bord : `kubectl -n tekton-pipelines port-forward svc/tekton-dashboard 9097:9097`,
puis ouvrir http://localhost:9097.

| Tâche | Image | Rôle |
|---|---|---|
| `fetch-source` | alpine/git | clone + SHA court (= tag des images) |
| `backend-tests` | maven:3.9-temurin-21 | `mvn verify` : compilation + 84 tests |
| `frontend-build` | node:22-alpine | `npm ci` + `ng build --configuration production` |
| `build-*` (×6, en parallèle) | kaniko | build + push des images **sans démon Docker** |
| `update-gitops` | alpine/git | met à jour `newTag` dans l'overlay prod, commit `[skip ci]`, push |

### 2.5 Déclenchement automatique (webhook GitHub)
- L'EventListener est exposé par l'Ingress `tekton-hooks.stock.local` (Service `el-stock-listener:8080`).
- Sur un cluster local, l'URL doit être publique : utilisez par exemple
  `kubectl -n stock-ci port-forward svc/el-stock-listener 8080` + `ngrok http 8080`.
- GitHub → *Settings → Webhooks → Add webhook* : URL publique, *Content type* `application/json`,
  *Secret* = valeur de `github-webhook-secret`, événement **push**.
- Seuls les push sur `main` déclenchent le pipeline. Les commits du bot (`[skip ci]`) sont ignorés, ce qui évite une boucle infinie.

---

## Partie 3 — Déploiement continu GitOps avec FluxCD

### 3.1 Installer Flux
```powershell
winget install FluxCD.Flux     # ou choco install flux
flux check --pre
flux install                   # contrôleurs dans le namespace flux-system
```
Alternative recommandée en production : `flux bootstrap github --owner=<compte> --repository=<depot-flux> --path=clusters/prod`.

### 3.2 Secrets chiffrés avec SOPS + age
```powershell
winget install FiloSottile.age Mozilla.SOPS
age-keygen -o age.agekey                         # affiche la clé publique age1...
kubectl -n flux-system create secret generic sops-age --from-file=age.agekey=age.agekey
```
1. Copier la clé publique dans `.sops.yaml` (racine du dépôt).
2. Renseigner les vraies valeurs dans `deploy/k8s/overlays/prod/secret.yaml`, puis le chiffrer :
   `sops --encrypt --in-place deploy/k8s/overlays/prod/secret.yaml`
3. Committer le fichier **chiffré**. Ne jamais committer `age.agekey`.

### 3.3 Connecter Flux au dépôt
```powershell
kubectl apply -k deploy/flux
flux get sources git
flux get kustomizations --watch
```
- `stock-management-app` synchronise `deploy/k8s/overlays/prod` : `prune` supprime ce qui est retiré
  de Git, `wait` attend la santé des Deployments.
- `stock-management-ci` synchronise aussi les ressources Tekton : la CI est elle-même gérée en GitOps.
- Forcer une synchronisation : `flux reconcile kustomization stock-management-app --with-source`.
- Notifications Teams/Slack (optionnel) : `deploy/flux/notifications.yaml`.

### 3.4 Démonstration de bout en bout
1. Modifier un libellé dans le frontend, puis `git commit` et `git push`.
2. Tekton : le pipeline démarre (tests, images `:<sha>`, commit GitOps).
3. Flux : le nouveau commit est détecté et les Deployments passent au nouveau tag (rolling update sans interruption).
4. Vérification : `kubectl -n stock-management get deploy -o wide` (colonne IMAGES).
5. Retour arrière : `git revert` du commit GitOps. Flux redéploie la version précédente.

---

## Validation hors-ligne des manifests

```bash
pip install pyyaml
python scripts/validate_manifests.py
```
Le script rend les overlays Kustomize et vérifie :
- la cohérence Kubernetes : sélecteurs, ports, clés de ConfigMap/Secret, Ingress, HPA, PDB ;
- la cohérence Tekton : paramètres, workspaces, résultats, triggers ;
- les chemins des Kustomizations Flux.

Résultat attendu : `OK : 258 vérifications, aucune erreur.`

## Bonnes pratiques mises en œuvre
- **Kubernetes** :
  - images non-root (UID numérique) avec `readOnlyRootFilesystem` et capacités supprimées ;
  - requêtes et limites de ressources ;
  - sondes *startup*, *liveness* et *readiness* (Actuator) ;
  - `RollingUpdate` sans indisponibilité (`maxUnavailable: 0`) ;
  - HPA et PDB en production ;
  - NetworkPolicy qui limite l'accès à PostgreSQL ;
  - une base par microservice.
- **Configuration** : ConfigMap pour les valeurs non sensibles, Secret (chiffré SOPS en production) pour les secrets. La découverte de services passe par le DNS Kubernetes.
- **CI** : tests avant tout build d'image, builds d'images en parallèle, images taguées par SHA de commit (traçabilité), Kaniko sans privilèges.
- **CD** : GitOps (pull), réconciliation continue, `prune`, retour arrière par `git revert`.
