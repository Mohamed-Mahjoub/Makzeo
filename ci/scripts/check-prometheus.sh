#!/usr/bin/env bash
# =============================================================================
# Vérifie, après un déploiement, que Prometheus collecte les métriques des 5 microservices
# et qu'aucune alerte critique de l'application n'est active.
# Utilise le proxy de l'API Kubernetes (droit "services/proxy" du compte ci-deployer).
#   KUBECONFIG=... bash ci/scripts/check-prometheus.sh
# =============================================================================
set -euo pipefail
PROM="/api/v1/namespaces/${PROM_NAMESPACE:-monitoring}/services/${PROM_SERVICE:-kps-prometheus:9090}/proxy"
EXPECTED="${EXPECTED_SERVICES:-5}"
TIMEOUT="${TIMEOUT_SECONDS:-300}"

query() {
  local encoded
  encoded="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "$1")"
  kubectl get --raw "${PROM}/api/v1/query?query=${encoded}"
}

echo "Attente de ${EXPECTED} services supervisés (délai maximal ${TIMEOUT} s)"
UP=0
for _ in $(seq 1 $((TIMEOUT / 15))); do
  UP="$(query 'count(count by (job) (up{namespace="stock-management", job=~".+-service"} == 1))' \
        | python3 -c 'import json,sys; r=json.load(sys.stdin)["data"]["result"]; print(int(float(r[0]["value"][1])) if r else 0)')"
  echo "  services joignables par Prometheus : ${UP}/${EXPECTED}"
  [ "${UP}" -ge "${EXPECTED}" ] && break
  sleep 15
done
[ "${UP}" -ge "${EXPECTED}" ] || { echo "##vso[task.logissue type=error]Seulement ${UP}/${EXPECTED} services supervisés"; exit 1; }

echo "Requêtes HTTP observées (5 dernières minutes) :"
query 'sum by (job) (increase(http_server_requests_seconds_count{namespace="stock-management"}[5m]))' \
  | python3 -c 'import json,sys
for r in json.load(sys.stdin)["data"]["result"]:
    print("  %-20s %8.0f" % (r["metric"].get("job", "?"), float(r["value"][1])))'

FIRING="$(query 'ALERTS{alertstate="firing", severity="critical", namespace="stock-management"}' \
          | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"]["result"]))')"
if [ "${FIRING}" -gt 0 ]; then
  echo "##vso[task.logissue type=error]${FIRING} alerte(s) critique(s) active(s) dans Prometheus"
  exit 1
fi
echo "Supervision conforme : ${UP} services collectés, aucune alerte critique."
