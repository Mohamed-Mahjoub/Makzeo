#!/usr/bin/env bash
# =============================================================================
# Prépare un nœud Ubuntu Server 24.04 pour Kubernetes (kubeadm + containerd).
# À exécuter sur les TROIS nœuds (master, worker1, worker2) :
#   sudo bash infra/vmware/prepare-node.sh
# =============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=../cluster.env
source "${SCRIPT_DIR}/../cluster.env"

[ "$(id -u)" -eq 0 ] || { echo "À lancer avec sudo"; exit 1; }

echo "==> 1/6 Résolution des noms des nœuds (/etc/hosts)"
sed -i '/# makzeo-lab/d' /etc/hosts
cat >> /etc/hosts <<HOSTS
${MASTER_IP} k8s-master   # makzeo-lab
${WORKER1_IP} k8s-worker1  # makzeo-lab
${WORKER2_IP} k8s-worker2  # makzeo-lab
${DEVOPS_IP} devops       # makzeo-lab
HOSTS

echo "==> 2/6 Désactivation du swap (exigé par kubelet)"
swapoff -a
sed -i '/\sswap\s/ s/^\([^#]\)/#\1/' /etc/fstab

echo "==> 3/6 Modules noyau et paramètres réseau"
cat > /etc/modules-load.d/k8s.conf <<CONF
overlay
br_netfilter
CONF
modprobe overlay
modprobe br_netfilter
cat > /etc/sysctl.d/k8s.conf <<CONF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
CONF
sysctl --system >/dev/null

echo "==> 4/6 Runtime de conteneurs containerd (cgroup systemd)"
apt-get update -q
apt-get install -y -q containerd apt-transport-https ca-certificates curl gpg
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
grep -q 'SystemdCgroup = true' /etc/containerd/config.toml || { echo "SystemdCgroup introuvable dans config.toml"; exit 1; }
# Configuration des registres par fichiers hosts.toml (registre Nexus en HTTP)
sed -i 's|config_path = ""|config_path = "/etc/containerd/certs.d"|' /etc/containerd/config.toml

echo "==> 5/6 Accès au registre Nexus ${NEXUS_REGISTRY}"
mkdir -p "/etc/containerd/certs.d/${NEXUS_REGISTRY}"
cat > "/etc/containerd/certs.d/${NEXUS_REGISTRY}/hosts.toml" <<TOML
server = "http://${NEXUS_REGISTRY}"

[host."http://${NEXUS_REGISTRY}"]
  capabilities = ["pull", "resolve"]
  skip_verify = true
TOML
systemctl restart containerd
systemctl enable containerd

echo "==> 6/6 kubelet, kubeadm, kubectl ${K8S_VERSION}"
mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL "https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/Release.key" \
  | gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/ /" \
  > /etc/apt/sources.list.d/kubernetes.list
apt-get update -q
apt-get install -y -q kubelet kubeadm kubectl
apt-mark hold kubelet kubeadm kubectl
systemctl enable --now kubelet

echo "Nœud prêt. Master : infra/vmware/init-master.sh ; worker : commande kubeadm join."
