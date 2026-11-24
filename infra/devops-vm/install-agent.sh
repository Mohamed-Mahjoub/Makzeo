#!/usr/bin/env bash
# =============================================================================
# Installe l'agent Azure Pipelines auto-hébergé sur le master (outils DevOps), en service systemd.
#   sudo AGENT_URL=<lien de téléchargement Linux x64> \
#        AZDO_URL=https://dev.azure.com/<organisation> AZDO_PAT=<jeton> \
#        bash infra/devops-vm/install-agent.sh
# Le lien de téléchargement est affiché dans Azure DevOps :
#   Project settings > Agent pools > Makzeo-OnPrem > New agent > Linux > x64
# Le jeton (PAT) doit avoir la portée "Agent Pools (Read & manage)".
# =============================================================================
set -euo pipefail
: "${AGENT_URL:?Définir AGENT_URL}"
: "${AZDO_URL:?Définir AZDO_URL}"
: "${AZDO_PAT:?Définir AZDO_PAT}"
POOL="${AZDO_POOL:-Makzeo-OnPrem}"
AGENT_NAME="${AGENT_NAME:-devops-vm}"
AGENT_USER=azdevops
AGENT_DIR=/opt/azagent

[ "$(id -u)" -eq 0 ] || { echo "À lancer avec sudo"; exit 1; }

echo "==> Utilisateur de service ${AGENT_USER} (membre du groupe docker)"
id "${AGENT_USER}" >/dev/null 2>&1 || useradd --create-home --shell /bin/bash "${AGENT_USER}"
usermod -aG docker "${AGENT_USER}"

echo "==> Téléchargement et dépendances de l'agent"
mkdir -p "${AGENT_DIR}"
curl -fsSL "${AGENT_URL}" | tar -xz -C "${AGENT_DIR}"
"${AGENT_DIR}/bin/installdependencies.sh"
chown -R "${AGENT_USER}:${AGENT_USER}" "${AGENT_DIR}"

echo "==> Enregistrement dans le pool ${POOL}"
sudo -u "${AGENT_USER}" bash -c "cd ${AGENT_DIR} && ./config.sh --unattended \
  --url '${AZDO_URL}' --auth pat --token '${AZDO_PAT}' \
  --pool '${POOL}' --agent '${AGENT_NAME}' --work _work --replace --acceptTeeEula"

echo "==> Service systemd"
cd "${AGENT_DIR}"
./svc.sh install "${AGENT_USER}"
./svc.sh start
./svc.sh status | head -5
echo "Agent ${AGENT_NAME} en ligne dans le pool ${POOL}."
