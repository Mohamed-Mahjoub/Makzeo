#!/usr/bin/env bash
# =============================================================================
# Initialise le plan de contrôle et le réseau des pods (Calico).
# À exécuter UNE fois, sur le master, avec l'utilisateur d'administration (pas root) :
#   bash infra/vmware/init-master.sh
# =============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../cluster.env"

echo "==> kubeadm init (API server sur ${MASTER_IP}, pods en ${POD_CIDR})"
sudo kubeadm init \
  --apiserver-advertise-address="${MASTER_IP}" \
  --pod-network-cidr="${POD_CIDR}" \
  --node-name k8s-master | tee "$HOME/kubeadm-init.log"

echo "==> kubeconfig de l'utilisateur $(whoami)"
mkdir -p "$HOME/.kube"
sudo cp /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"

echo "==> Calico ${CALICO_VERSION} (réseau des pods + NetworkPolicy)"
kubectl apply -f "https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/calico.yaml"
kubectl -n kube-system rollout status daemonset/calico-node --timeout=300s

echo
echo "Plan de contrôle prêt. Commande à lancer (avec sudo) sur chaque worker :"
kubeadm token create --print-join-command
