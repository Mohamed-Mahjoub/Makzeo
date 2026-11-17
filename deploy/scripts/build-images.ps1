# Construit les 6 images Docker localement (tag "latest") - Windows PowerShell.
# Avec minikube, exécuter d'abord :  & minikube -p minikube docker-env --shell powershell | Invoke-Expression
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..\..")
$services = "auth-service", "product-service", "supplier-service", "stock-service", "reporting-service"
foreach ($svc in $services) {
    Write-Host "==> $svc" -ForegroundColor Cyan
    docker build --build-arg SERVICE=$svc -t "stock-management/${svc}:latest" backend
    if ($LASTEXITCODE -ne 0) { throw "Echec du build de $svc" }
}
Write-Host "==> frontend" -ForegroundColor Cyan
docker build -t stock-management/frontend:latest frontend
if ($LASTEXITCODE -ne 0) { throw "Echec du build du frontend" }
docker images | Select-String "stock-management"
