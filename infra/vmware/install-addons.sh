#!/usr/bin/env bash
# =============================================================================
# Installe les composants du cluster, une fois les 2 workers joints :
#   stockage local-path, metrics-server, contrôleur Ingress nginx, Prometheus + Grafana,
#   namespace applicatif et compte de service du pipeline.
# À exécuter sur le master :  bash infra/vmware/install-addons.sh
# Variable obligatoire : GRAFANA_ADMIN_PASSWORD (mot de passe de l'administrateur Grafana)
# =============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/../cluster.env"
: "${GRAFANA_ADMIN_PASSWORD:?Définir GRAFANA_ADMIN_PASSWORD avant de lancer le script}"

kubectl get nodes

echo "==> 1/6 Stockage persistant : local-path-provisioner ${LOCAL_PATH_VERSION} (classe par défaut)"
kubectl apply -f "https://raw.githubusercontent.com/rancher/local-path-provisioner/${LOCAL_PATH_VERSION}/deploy/local-path-storage.yaml"
kubectl patch storageclass local-path \
  -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'

echo "==> 2/6 metrics-server (nécessaire aux HorizontalPodAutoscaler)"
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
# Les certificats des kubelet installés par kubeadm sont auto-signés
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'

echo "==> 3/6 Helm"
if ! command -v helm >/dev/null; then
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
fi
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

echo "==> 4/6 Contrôleur Ingress nginx (ports 80/443 ouverts sur chaque worker)"
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace \
  --set controller.kind=DaemonSet \
  --set controller.hostPort.enabled=true \
  --set controller.service.type=NodePort \
  --set controller.metrics.enabled=true \
  --wait --timeout 10m

echo "==> 5/6 Supervision : Prometheus, Alertmanager, Grafana (kube-prometheus-stack)"
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
kubectl -n monitoring create secret generic grafana-admin \
  --from-literal=admin-user=admin \
  --from-literal=admin-password="${GRAFANA_ADMIN_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f "${REPO_DIR}/deploy/monitoring/kube-prometheus-stack-values.yaml" \
  --set "grafana.ingress.hosts[0]=${GRAFANA_HOST}" \
  --set "prometheus.ingress.hosts[0]=${PROMETHEUS_HOST}" \
  --wait --timeout 15m

echo "==> 6/6 Namespace applicatif et compte de service du pipeline"
kubectl apply -f "${SCRIPT_DIR}/ci-deployer-rbac.yaml"

echo
echo "Cluster prêt."
echo "  Grafana    : http://${GRAFANA_HOST}  (admin / GRAFANA_ADMIN_PASSWORD)"
echo "  Prometheus : http://${PROMETHEUS_HOST}"
echo "Étape suivante : bash infra/vmware/create-ci-kubeconfig.sh"
