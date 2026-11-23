# 5. Infrastructure : VMware, Kubernetes et outils DevOps

## 5.1 Topologie

Trois machines virtuelles Ubuntu Server 24.04 LTS sur VMware Workstation, reliées par le réseau
NAT `VMnet8` (accès Internet pour les paquets et les images). Le master porte aussi les outils
DevOps, qui tournent dans Docker, hors du cluster : le master refuse par défaut les pods
applicatifs (taint `control-plane`), si bien que les deux rôles cohabitent sans conflit.

| VM | Nom d'hôte | IP | vCPU | RAM | Disque | Rôle |
|---|---|---|---|---|---|---|
| master + devops | k8s-master | 192.168.80.10 | 6 | 12 Go (10 Go minimum) | 100 Go | Plan de contrôle Kubernetes ; SonarQube, Nexus et agent Azure Pipelines (Docker) |
| worker1 | k8s-worker1 | 192.168.80.11 | 4 | 6 Go | 50 Go | Charges applicatives, Ingress |
| worker2 | k8s-worker2 | 192.168.80.12 | 4 | 6 Go | 50 Go | Charges applicatives, Ingress |

Total : 14 vCPU et 24 Go de RAM (22 Go au minimum) ; un PC de 32 Go est recommandé. SonarQube
(environ 2,5 Go) et Nexus (environ 2 Go) sont les plus gourmands : sous 10 Go, le plan de
contrôle ralentit pendant les builds.

```
                PC Windows (VMware Workstation) ─── Internet ─── Azure DevOps (dev.azure.com)
                             │ VMnet8 (NAT) 192.168.80.0/24                ▲
       ┌─────────────────────┼──────────────────────┐                      │ HTTPS sortant
┌──────▼──────────────┐ ┌────▼───────┐       ┌──────▼─────┐                │
│ master + devops     ├─┤ worker1    │       │ worker2    │                │
│ API :6443, etcd     │ │ Ingress :80│       │ Ingress :80│                │
│ Calico              │ │ pods app   │       │ pods app   │                │
│ --- Docker -------- │ │ Prometheus │       │ Grafana    │                │
│ SonarQube :9000     │ └────────────┘       └────────────┘                │
│ Nexus :8081, :8083  │                                                    │
│ agent Azure ────────┼────────────────────────────────────────────────────┘
└─────────────────────┘
```

Les ports des deux rôles ne se recouvrent pas : Kubernetes utilise 6443, 2379-2380, 10250, 10257
et 10259 ; Nexus 8081 et 8083 ; SonarQube 9000. Docker et Kubernetes partagent le paquet
`containerd` d'Ubuntu, chacun dans son espace de noms.

L'agent Azure Pipelines n'ouvre que des connexions **sortantes** vers Azure DevOps : aucun port
du PC n'a besoin d'être exposé sur Internet.

## 5.2 Préparation du réseau VMware

1. VMware Workstation > **Edit > Virtual Network Editor** > `VMnet8` (NAT) : noter le sous-réseau
   (ex. `192.168.80.0`). S'il est différent, adapter **uniquement** `infra/cluster.env` et les
   adresses du pipeline (`azure-pipelines.yml`, section `variables`) et de l'overlay
   (`deploy/k8s/overlays/onprem/kustomization.yaml`).
2. Passerelle NAT : `192.168.80.2` (bouton *NAT Settings*). Les adresses `.10` à `.20` doivent
   être hors de la plage DHCP (*DHCP Settings*, par défaut `.128` à `.254`).

## 5.3 Création des VM

Pour chaque VM : *Create a New Virtual Machine* > *Typical* > ISO Ubuntu Server 24.04 > ressources
du tableau 5.1 > réseau **NAT**. Pendant l'installation :

- profil réseau : IPv4 **manuel** — sous-réseau `192.168.80.0/24`, adresse de la VM,
  passerelle `192.168.80.2`, DNS `192.168.80.2` ;
- nom d'hôte conforme au tableau ; utilisateur d'administration (ex. `makzeo`) ;
- cocher **Install OpenSSH server** ; ne sélectionner aucun snap.

Après le premier démarrage :

```bash
sudo apt update && sudo apt -y full-upgrade && sudo reboot
```

Astuce : créer la VM master, l'éteindre, puis la **cloner** (*Manage > Clone > Full clone*) pour
les workers ; sur chaque clone, changer le nom d'hôte (`sudo hostnamectl set-hostname ...`),
l'adresse IP (`/etc/netplan/50-cloud-init.yaml`, puis `sudo netplan apply`) et régénérer
l'identifiant machine (`sudo rm /etc/machine-id && sudo systemd-machine-id-setup`).

Depuis Windows, se connecter en SSH : `ssh makzeo@192.168.80.10`.

## 5.4 Récupération du dépôt sur les VM

Sur chaque VM :

```bash
sudo apt -y install git
git clone https://<organisation>@dev.azure.com/<organisation>/Makzeo/_git/makzeo
cd makzeo
```

(Utiliser un jeton d'accès personnel en mot de passe ; ou copier le dossier `infra/` avec `scp`.)

## 5.5 Cluster Kubernetes (kubeadm)

### Étape 1 — Préparer les trois nœuds (master, worker1, worker2)

```bash
sudo bash infra/vmware/prepare-node.sh
```

Le script configure `/etc/hosts`, désactive le swap, charge `overlay` et `br_netfilter`, active le
routage IP, installe **containerd** (cgroup systemd, accès au registre Nexus en HTTP) et
**kubeadm / kubelet / kubectl** v1.35 (paquets bloqués en version).

### Étape 2 — Initialiser le master

```bash
bash infra/vmware/init-master.sh        # sur le master, sans sudo
```

`kubeadm init` crée le plan de contrôle, le kubeconfig de l'utilisateur est installé dans
`~/.kube/config` et le réseau **Calico** (compatible NetworkPolicy) est déployé. Le script affiche
la commande `kubeadm join ...`.

### Étape 3 — Joindre les workers

Sur worker1 puis worker2, exécuter avec `sudo` la commande affichée. Si elle est perdue :
`kubeadm token create --print-join-command` sur le master.

Vérification sur le master (tous les nœuds `Ready` après 1 à 2 minutes) :

```bash
kubectl get nodes -o wide
```

### Étape 4 — Installer les composants du cluster

```bash
export GRAFANA_ADMIN_PASSWORD='<mot de passe fort>'
bash infra/vmware/install-addons.sh
```

| Composant | Rôle |
|---|---|
| local-path-provisioner | Volumes persistants (PostgreSQL, Prometheus, Grafana), classe par défaut |
| metrics-server | Métriques CPU / mémoire pour `kubectl top` et les HPA |
| ingress-nginx | Point d'entrée HTTP sur le port 80 de chaque worker (DaemonSet, hostPort) |
| kube-prometheus-stack | Prometheus, Alertmanager, Grafana, node-exporter, kube-state-metrics |
| ci-deployer-rbac.yaml | Namespace `stock-management` et compte de service du pipeline |

Vérification :

```bash
kubectl get pods -A                     # tout doit être Running
kubectl top nodes
```

Accès : Grafana `http://grafana.192.168.80.11.nip.io` (admin / `GRAFANA_ADMIN_PASSWORD`),
Prometheus `http://prometheus.192.168.80.11.nip.io`.

> `nip.io` est un DNS public qui résout `<nom>.<IP>.nip.io` vers `<IP>` : aucune modification du
> fichier `hosts` n'est nécessaire. Sans Internet, ajouter les noms dans
> `C:\Windows\System32\drivers\etc\hosts`.

### Étape 5 — Kubeconfig du pipeline

```bash
bash infra/vmware/create-ci-kubeconfig.sh
```

Le fichier `~/makzeo-ci-kubeconfig` contient le jeton du compte `ci-deployer`, limité au
namespace `stock-management`. Le récupérer sur le PC (`scp makzeo@192.168.80.10:makzeo-ci-kubeconfig .`)
pour l'importer dans Azure DevOps (voir chapitre 6), puis le supprimer du PC.

Le kubeconfig administrateur (`/etc/kubernetes/admin.conf`) ne doit **jamais** quitter le master.

## 5.6 Outils DevOps sur le master

Toutes les commandes de cette section s'exécutent **sur le master**, une fois le cluster prêt
(section 5.5 terminée). L'ordre compte : Kubernetes d'abord, Docker ensuite, pour que la
configuration de containerd faite par `prepare-node.sh` soit en place avant l'arrivée de Docker.

### Étape 1 — Outils

```bash
sudo bash infra/devops-vm/install-devops-tools.sh
sudo reboot
```

Installe Docker (registre Nexus autorisé en HTTP), JDK 21, Maven, Node.js 22, kubectl, kustomize,
Python 3, jq, et applique les paramètres noyau exigés par SonarQube (`vm.max_map_count`).

### Étape 2 — SonarQube et Nexus

```bash
cd infra/devops-vm
cp .env.example .env && nano .env        # SONAR_DB_PASSWORD
sudo docker compose up -d
sudo docker compose ps                    # 3 conteneurs Up ; SonarQube et Nexus répondent après 2 à 4 minutes
```

### Étape 3 — Configurer Nexus

```bash
sudo NEXUS_ADMIN_PASSWORD='<admin>' NEXUS_CI_PASSWORD='<compte CI>' bash configure-nexus.sh
```

| Dépôt | Type | Usage |
|---|---|---|
| maven-public | groupe | Miroir Maven Central utilisé par le build |
| maven-snapshots / maven-releases | hébergé | Publication des JAR de Makzeo |
| npm-proxy | proxy | Cache de registry.npmjs.org |
| docker-hosted | hébergé, port 8083 | Registre des images Makzeo |

Interface : `http://192.168.80.10:8081` (admin / `NEXUS_ADMIN_PASSWORD`).

### Étape 4 — Configurer SonarQube

```bash
SONAR_ADMIN_PASSWORD='<mot de passe>' bash configure-sonarqube.sh
```

Le mot de passe doit contenir au moins 12 caractères avec majuscule, minuscule, chiffre et
caractère spécial. Le script crée la Quality Gate « Makzeo » et affiche le **jeton d'analyse** à
conserver pour Azure DevOps (`SONAR_TOKEN`). Interface : `http://192.168.80.10:9000`.

### Étape 5 — Agent Azure Pipelines

Voir chapitre 6, étape 6.2.

## 5.7 Arrêt et redémarrage du laboratoire

- Arrêt : éteindre proprement (`sudo poweroff`) les workers, puis le master.
- Démarrage : le master, puis les workers. Le cluster et les conteneurs SonarQube / Nexus
  redémarrent automatiquement ; vérifier `kubectl get nodes` et `docker compose ps`.
- Prendre un **instantané VMware** de chaque VM après l'étape 5.5-4 et après l'étape 5.6-4.
