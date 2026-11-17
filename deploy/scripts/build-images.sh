#!/usr/bin/env bash
# Construit les 6 images Docker localement (tag "latest").
# Avec minikube, exécuter d'abord :  eval $(minikube docker-env)
set -euo pipefail
cd "$(dirname "$0")/../.."
for svc in auth-service product-service supplier-service stock-service reporting-service; do
  echo "==> $svc"
  docker build --build-arg SERVICE="$svc" -t "stock-management/$svc:latest" backend
done
echo "==> frontend"
docker build -t stock-management/frontend:latest frontend
docker images | grep stock-management
