#!/usr/bin/env bash
# =============================================================================
# Prépare la VM "devops" (Ubuntu Server 24.04) : Docker, JDK 21, Maven, Node.js 22,
# kubectl, kustomize, Python, et paramètres système exigés par SonarQube.
#   sudo bash infra/devops-vm/install-devops-tools.sh
# =============================================================================
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "${SCRIPT_DIR}/../cluster.env"
KUSTOMIZE_VERSION=v5.4.3

[ "$(id -u)" -eq 0 ] || { echo "À lancer avec sudo"; exit 1; }

echo "==> 1/6 Paramètres noyau pour SonarQube (Elasticsearch embarqué)"
cat > /etc/sysctl.d/99-sonarqube.conf <<CONF
vm.max_map_count=524288
fs.file-max=131072
CONF
sysctl --system >/dev/null

echo "==> 2/6 Paquets de base, JDK 21, Maven, Python"
apt-get update -q
apt-get install -y -q ca-certificates curl gpg git jq unzip openjdk-21-jdk-headless maven python3 python3-yaml

echo "==> 3/6 Docker Engine et Docker Compose"
apt-get install -y -q docker.io docker-compose-v2
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<JSON
{
  "insecure-registries": ["${NEXUS_REGISTRY}"],
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl enable --now docker
systemctl restart docker

echo "==> 4/6 Node.js 22"
if ! node --version 2>/dev/null | grep -q '^v22'; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y -q nodejs
fi

echo "==> 5/6 kubectl ${K8S_VERSION}"
mkdir -p -m 755 /etc/apt/keyrings
curl -fsSL "https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/Release.key" \
  | gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/ /" \
  > /etc/apt/sources.list.d/kubernetes.list
apt-get update -q
apt-get install -y -q kubectl

echo "==> 6/6 kustomize ${KUSTOMIZE_VERSION}"
curl -fsSL -o /tmp/kustomize.tgz \
  "https://github.com/kubernetes-sigs/kustomize/releases/download/kustomize%2F${KUSTOMIZE_VERSION}/kustomize_${KUSTOMIZE_VERSION}_linux_amd64.tar.gz"
tar -xzf /tmp/kustomize.tgz -C /usr/local/bin kustomize

echo
java -version 2>&1 | head -1; mvn -v | head -1; node --version; docker --version; kubectl version --client | head -1; kustomize version
echo "Outils installés. Étape suivante : docker compose up -d (dans infra/devops-vm)."
