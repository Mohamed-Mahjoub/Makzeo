#!/usr/bin/env bash
# =============================================================================
# Génère le kubeconfig du compte de service "ci-deployer" (droits limités),
# à importer dans Azure DevOps : Pipelines > Library > Secure files > makzeo-kubeconfig
# À exécuter sur le master :  bash infra/vmware/create-ci-kubeconfig.sh
# =============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../cluster.env"
OUT="${1:-$HOME/makzeo-ci-kubeconfig}"

TOKEN=""
for _ in $(seq 1 30); do
  TOKEN="$(kubectl -n stock-management get secret ci-deployer-token -o jsonpath='{.data.token}' 2>/dev/null | base64 -d || true)"
  [ -n "${TOKEN}" ] && break
  sleep 2
done
[ -n "${TOKEN}" ] || { echo "Jeton du compte ci-deployer introuvable"; exit 1; }
CA="$(kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')"

cat > "${OUT}" <<KUBECONFIG
apiVersion: v1
kind: Config
clusters:
  - name: makzeo-lab
    cluster:
      server: https://${MASTER_IP}:6443
      certificate-authority-data: ${CA}
users:
  - name: ci-deployer
    user:
      token: ${TOKEN}
contexts:
  - name: ci-deployer@makzeo-lab
    context:
      cluster: makzeo-lab
      namespace: stock-management
      user: ci-deployer
current-context: ci-deployer@makzeo-lab
KUBECONFIG
chmod 600 "${OUT}"

echo "Fichier généré : ${OUT}"
echo "Test des droits :"
KUBECONFIG="${OUT}" kubectl auth can-i create deployments -n stock-management
KUBECONFIG="${OUT}" kubectl auth can-i delete nodes || true
