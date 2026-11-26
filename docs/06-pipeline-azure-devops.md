# 6. Pipeline Azure DevOps

## 6.1 Principe

Le pipeline `azure-pipelines.yml` s'exécute sur un **agent auto-hébergé** installé sur la VM
devops : c'est la seule machine qui atteint à la fois SonarQube, Nexus et le cluster, situés sur
le réseau privé VMware. Les artefacts passent d'un stage à l'autre par le dossier local
`$(Pipeline.Workspace)/makzeo-artifacts` (un seul agent), ce qui garantit que **les images
contiennent exactement les JAR et l'interface testés** (« build once, deploy many »).

| Stage | Déclencheur | Contenu | Échec si |
|---|---|---|---|
| **Build** | push et pull request | Backend : `mvn verify` (ou `deploy` sur main), tests JUnit, couverture JaCoCo, SonarQube, publication Maven dans Nexus. Frontend : `npm ci` via Nexus, build production, SonarQube, Trivy (dépendances). Contrôle des manifestes. | test en échec, Quality Gate rouge, vulnérabilité HIGH/CRITICAL corrigeable, manifeste incohérent |
| **Images** | main | 7 images à partir des artefacts du stage Build, analyse Trivy, push `:<build>` et `:latest` dans le registre Nexus | vulnérabilité CRITICAL corrigeable |
| **Deploy** | main, après approbation | Secrets Kubernetes, `kustomize edit set image`, `kubectl apply --dry-run=server` puis `apply`, attente des rollouts, archivage des manifestes | manifeste refusé, pod non prêt en 7 min |
| **Smoke** | main | `e2e_test.py` via l'Ingress | scénario fonctionnel en erreur |
| **Monitoring** | main | Prometheus collecte les 6 services ; aucune alerte critique Makzeo active | cible absente, alerte critique |

Le numéro de build (`20260512.3`) sert de tag d'image et de version SonarQube : chaque
déploiement est traçable jusqu'au commit.

## 6.2 Mise en place (une seule fois)

Prérequis : chapitre 5 terminé (cluster prêt, SonarQube et Nexus configurés).

### 1. Projet et dépôt

1. <https://dev.azure.com> > **New project** `Makzeo` (privé).
2. **Repos > Files > Import repository** (depuis GitHub, cocher *Requires authentication* et
   fournir un jeton GitHub), ou pousser le dépôt local :
   ```bash
   git remote add azure https://<organisation>@dev.azure.com/<organisation>/Makzeo/_git/makzeo
   git push azure main
   ```

### 2. Pool d'agents et agent

1. **Project settings > Agent pools > Add pool** : type *Self-hosted*, nom **`Makzeo-OnPrem`**,
   cocher *Grant access permission to all pipelines*.
2. Ouvrir le pool > **New agent** > Linux > x64 : copier le lien de téléchargement.
3. **User settings > Personal access tokens > New token** : portée *Agent Pools (Read & manage)*,
   durée courte.
4. Sur le master (outils DevOps) :
   ```bash
   sudo AGENT_URL='<lien>' AZDO_URL='https://dev.azure.com/<organisation>' AZDO_PAT='<jeton>' \
        bash infra/devops-vm/install-agent.sh
   ```
5. Le pool affiche l'agent `devops-vm` **Online**. Révoquer ensuite le jeton (il n'est plus utile).

L'agent tourne sous l'utilisateur `azdevops`, membre du groupe `docker`, en service systemd.

### 3. Groupe de variables `makzeo-secrets`

**Pipelines > Library > + Variable group** `makzeo-secrets` ; cliquer sur le cadenas de chaque
valeur pour la rendre secrète :

| Variable | Valeur |
|---|---|
| `JWT_SECRET` | `openssl rand -base64 48` |
| `POSTGRES_PASSWORD` | mot de passe fort |
| `ADMIN_PASSWORD` | mot de passe du compte admin de l'application (≥ 8 caractères) |
| `NEXUS_CI_PASSWORD` | mot de passe du compte Nexus `ci-deployer` |
| `SONAR_TOKEN` | jeton affiché par `configure-sonarqube.sh` |
| `AI_API_KEY` | *facultatif* : clé de l'API Claude du module IA achats |
| `SERPER_API_KEY`, `TAVILY_API_KEY` | *facultatifs* : veille web sans API Claude (Serper + Tavily) |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | *facultatifs* : compte d'un SMTP réel (sinon Mailpit) |

Onglet **Pipeline permissions** : autoriser le pipeline (ou le faire au premier lancement).

### 4. Fichier sécurisé `makzeo-kubeconfig`

**Pipelines > Library > Secure files > + Secure file** : téléverser `makzeo-ci-kubeconfig`
(chapitre 5, étape 5) et le renommer **`makzeo-kubeconfig`**. Autoriser le pipeline.

### 5. Environnement et approbation

**Pipelines > Environments > New environment** `makzeo-onprem` (*None*) > menu **⋮ >
Approvals and checks > Approvals** : ajouter le ou les approbateurs.

### 6. Création du pipeline

**Pipelines > New pipeline > Azure Repos Git > makzeo > Existing Azure Pipelines YAML file** >
`/azure-pipelines.yml` > **Run**. Au premier lancement, accepter les demandes d'autorisation
(*Permit*) du pool, du groupe de variables, du fichier sécurisé et de l'environnement.

La connexion au registre Docker Nexus se fait dans le pipeline avec le compte `ci-deployer`
(`docker login --password-stdin`) : aucune connexion de service n'est nécessaire.

### 7. Protection de la branche main (recommandé)

**Repos > Branches > main > Branch policies** : *Require a minimum number of reviewers*,
*Build validation* avec ce pipeline. Une pull request exécute alors le stage Build (tests,
SonarQube, Trivy) avant toute fusion.

## 6.3 Déroulement d'une exécution

1. Push sur `main` → déclenchement automatique (les modifications limitées à `docs/` ou aux
   fichiers `.md` ne déclenchent rien).
2. **Build** : onglet *Tests* (résultats JUnit), onglet *Code Coverage* (JaCoCo) ; projets
   `makzeo-backend` et `makzeo-frontend` dans SonarQube ; artefacts
   `ma.pfe.stock:*:1.0.0-SNAPSHOT` dans Nexus `maven-snapshots`.
3. **Images** : `makzeo/<service>:<build>` dans Nexus (*Browse > docker-hosted*).
4. **Deploy** : en attente d'approbation (*Review > Approve*), puis déploiement.
5. **Smoke** et **Monitoring** : validation fonctionnelle et supervision.

Vérification manuelle :

```bash
kubectl -n stock-management get pods,svc,ingress,hpa
```

Application : <http://makzeo.192.168.80.11.nip.io>.

## 6.4 Retour arrière

Chaque build publie un tag d'image immuable. Pour revenir à la version précédente :

```bash
kubectl -n stock-management rollout undo deployment/<service>
```

ou ouvrir l'exécution d'un build antérieur et relancer son stage **Deploy** (*Rerun stage*),
qui redéploie ses images.
L'artefact `manifests` de chaque exécution contient les manifestes exacts appliqués.

## 6.5 Adapter le pipeline

| Besoin | Où |
|---|---|
| Autre plan d'adressage | `variables` de `azure-pipelines.yml`, `infra/cluster.env`, overlay `onprem` |
| Seuil de couverture, règles qualité | Quality Gate « Makzeo » dans SonarQube |
| Sévérité Trivy bloquante | options `--severity` des étapes Trivy |
| Nombre de réplicas | `deploy/k8s/overlays/onprem/hpa.yaml` |
