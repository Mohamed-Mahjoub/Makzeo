#!/usr/bin/env bash
# =============================================================================
# Configure SonarQube après son premier démarrage :
#   - nouveau mot de passe administrateur (le mot de passe initial admin/admin est désactivé) ;
#   - Quality Gate "Makzeo" appliquée par défaut à tous les projets ;
#   - jeton d'analyse pour le pipeline (à enregistrer dans Azure DevOps : SONAR_TOKEN).
# Variable obligatoire : SONAR_ADMIN_PASSWORD
#   SONAR_ADMIN_PASSWORD=... bash infra/devops-vm/configure-sonarqube.sh
# =============================================================================
set -euo pipefail
: "${SONAR_ADMIN_PASSWORD:?Définir SONAR_ADMIN_PASSWORD}"
SONAR="${SONAR_URL:-http://localhost:9000}"

echo "==> Attente du démarrage de SonarQube"
for _ in $(seq 1 90); do
  curl -s "${SONAR}/api/system/status" | grep -q '"status":"UP"' && break
  sleep 5
done

echo "==> Mot de passe administrateur"
if [ "$(curl -s -o /dev/null -w '%{http_code}' -u admin:admin "${SONAR}/api/authentication/validate")" = "200" ] \
   && curl -s -u admin:admin "${SONAR}/api/authentication/validate" | grep -q '"valid":true'; then
  curl -fsS -u admin:admin -X POST "${SONAR}/api/users/change_password" \
    --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=${SONAR_ADMIN_PASSWORD}"
fi
AUTH=(-fsS -u "admin:${SONAR_ADMIN_PASSWORD}")

echo "==> Quality Gate Makzeo (conditions sur le nouveau code)"
if ! curl "${AUTH[@]}" "${SONAR}/api/qualitygates/list" | jq -e '.qualitygates[] | select(.name=="Makzeo")' >/dev/null; then
  curl "${AUTH[@]}" -X POST "${SONAR}/api/qualitygates/create" --data-urlencode "name=Makzeo" >/dev/null
  add() { curl "${AUTH[@]}" -X POST "${SONAR}/api/qualitygates/create_condition" \
            --data-urlencode "gateName=Makzeo" --data-urlencode "metric=$1" \
            --data-urlencode "op=$2" --data-urlencode "error=$3" >/dev/null; }
  add new_reliability_rating  GT 1    # aucun nouveau bug (note A)
  add new_security_rating     GT 1    # aucune nouvelle vulnérabilité (note A)
  add new_maintainability_rating GT 1 # dette technique maîtrisée (note A)
  add new_coverage            LT 60   # au moins 60 % de couverture du nouveau code
  add new_duplicated_lines_density GT 5
fi
curl "${AUTH[@]}" -X POST "${SONAR}/api/qualitygates/set_as_default" --data-urlencode "name=Makzeo"

echo "==> Jeton d'analyse du pipeline"
curl "${AUTH[@]}" -X POST "${SONAR}/api/user_tokens/revoke" --data-urlencode "name=azure-devops" >/dev/null || true
TOKEN="$(curl "${AUTH[@]}" -X POST "${SONAR}/api/user_tokens/generate" \
          --data-urlencode "name=azure-devops" --data-urlencode "type=GLOBAL_ANALYSIS_TOKEN" | jq -r '.token')"
echo
echo "SonarQube configuré : ${SONAR}"
echo "Jeton à enregistrer dans Azure DevOps (groupe makzeo-secrets, variable SONAR_TOKEN, secrète) :"
echo "${TOKEN}"
