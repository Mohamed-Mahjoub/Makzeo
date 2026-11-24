#!/usr/bin/env bash
# =============================================================================
# Configure Nexus Repository après son premier démarrage :
#   - nouveau mot de passe administrateur, accès anonyme désactivé ;
#   - registre Docker hébergé "docker-hosted" (port 8083) et proxy npm "npm-proxy" ;
#   - utilisateur technique "ci-deployer" (droits limités) pour le pipeline.
# Les dépôts Maven maven-releases, maven-snapshots, maven-central et maven-public existent déjà.
# Variables obligatoires : NEXUS_ADMIN_PASSWORD, NEXUS_CI_PASSWORD
#   sudo NEXUS_ADMIN_PASSWORD=... NEXUS_CI_PASSWORD=... bash infra/devops-vm/configure-nexus.sh
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")"   # commandes docker compose du projet devops-tools
: "${NEXUS_ADMIN_PASSWORD:?Définir NEXUS_ADMIN_PASSWORD}"
: "${NEXUS_CI_PASSWORD:?Définir NEXUS_CI_PASSWORD}"
NEXUS="${NEXUS_URL:-http://localhost:8081}"
API="${NEXUS}/service/rest/v1"

echo "==> Attente du démarrage de Nexus (2 à 5 minutes au premier lancement)"
for _ in $(seq 1 90); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' "${API}/status/writable")" = "200" ] && break
  sleep 5
done

echo "==> Mot de passe administrateur"
if docker compose exec -T nexus test -f /nexus-data/admin.password 2>/dev/null; then
  INITIAL="$(docker compose exec -T nexus cat /nexus-data/admin.password)"
  curl -fsS -u "admin:${INITIAL}" -X PUT -H "Content-Type: text/plain" \
    --data "${NEXUS_ADMIN_PASSWORD}" "${API}/security/users/admin/change-password"
  docker compose exec -T nexus rm -f /nexus-data/admin.password
fi
AUTH=(-fsS -u "admin:${NEXUS_ADMIN_PASSWORD}" -H "Content-Type: application/json")

echo "==> Accès anonyme désactivé"
curl "${AUTH[@]}" -X PUT "${API}/security/anonymous" \
  -d '{"enabled": false, "userId": "anonymous", "realmName": "NexusAuthorizingRealm"}' >/dev/null

echo "==> Royaumes d'authentification (dont le jeton Docker)"
AVAILABLE="$(curl "${AUTH[@]}" "${API}/security/realms/available" | jq -r '.[].id')"
ACTIVE="[]"
for realm in NexusAuthenticatingRealm NexusAuthorizingRealm DockerToken; do
  if echo "${AVAILABLE}" | grep -qx "${realm}"; then
    ACTIVE="$(echo "${ACTIVE}" | jq --arg r "${realm}" '. + [$r]')"
  fi
done
curl "${AUTH[@]}" -X PUT "${API}/security/realms/active" -d "${ACTIVE}"

repo_exists() { curl -s -o /dev/null -w '%{http_code}' -u "admin:${NEXUS_ADMIN_PASSWORD}" "${API}/repositories/$1" | grep -q 200; }

echo "==> Registre Docker hébergé docker-hosted (HTTP, port 8083)"
repo_exists docker-hosted || curl "${AUTH[@]}" -X POST "${API}/repositories/docker/hosted" -d '{
  "name": "docker-hosted",
  "online": true,
  "storage": { "blobStoreName": "default", "strictContentTypeValidation": true, "writePolicy": "allow" },
  "docker": { "v1Enabled": false, "forceBasicAuth": true, "httpPort": 8083 }
}'

echo "==> Proxy npm npm-proxy (cache de registry.npmjs.org)"
repo_exists npm-proxy || curl "${AUTH[@]}" -X POST "${API}/repositories/npm/proxy" -d '{
  "name": "npm-proxy",
  "online": true,
  "storage": { "blobStoreName": "default", "strictContentTypeValidation": true },
  "proxy": { "remoteUrl": "https://registry.npmjs.org", "contentMaxAge": 1440, "metadataMaxAge": 1440 },
  "negativeCache": { "enabled": true, "timeToLive": 1440 },
  "httpClient": { "blocked": false, "autoBlock": true }
}'

echo "==> Rôle et utilisateur technique ci-deployer"
curl -s -o /dev/null -u "admin:${NEXUS_ADMIN_PASSWORD}" "${API}/security/roles/ci-deployer-role" -w '%{http_code}' | grep -q 200 || \
curl "${AUTH[@]}" -X POST "${API}/security/roles" -d '{
  "id": "ci-deployer-role",
  "name": "ci-deployer-role",
  "description": "Pipeline Azure DevOps : lecture et publication Maven, npm et Docker",
  "privileges": [
    "nx-repository-view-maven2-*-*",
    "nx-repository-view-npm-*-*",
    "nx-repository-view-docker-docker-hosted-*"
  ],
  "roles": []
}'
if curl "${AUTH[@]}" "${API}/security/users?userId=ci-deployer" | jq -e 'length > 0' >/dev/null; then
  curl "${AUTH[@]}" -X PUT -H "Content-Type: text/plain" --data "${NEXUS_CI_PASSWORD}" \
    "${API}/security/users/ci-deployer/change-password"
else
  curl "${AUTH[@]}" -X POST "${API}/security/users" -d "{
    \"userId\": \"ci-deployer\",
    \"firstName\": \"Pipeline\",
    \"lastName\": \"Azure DevOps\",
    \"emailAddress\": \"ci-deployer@makzeo.local\",
    \"password\": \"${NEXUS_CI_PASSWORD}\",
    \"status\": \"active\",
    \"roles\": [\"ci-deployer-role\"]
  }" >/dev/null
fi

echo
echo "Nexus configuré : ${NEXUS}"
echo "  Maven : ${NEXUS}/repository/maven-public/   (publication : maven-releases, maven-snapshots)"
echo "  npm   : ${NEXUS}/repository/npm-proxy/"
echo "  Docker: <IP devops>:8083   (docker login avec ci-deployer)"
