# Makzeo — Déploiement

| Dossier | Contenu |
|---|---|
| `docker/gateway` | Passerelle Nginx de Docker Compose (routage `/api/*`, limitation des connexions) |
| `docker/postgres` | Création des 4 bases au premier démarrage |
| `k8s/base` | Manifestes Kubernetes de l'application (Deployments, Services, Ingress, NetworkPolicy, PostgreSQL) |
| `k8s/overlays/onprem` | Cluster VMware : registre Nexus, noms d'hôtes, HPA, PDB, ServiceMonitor, alertes, tableau de bord |
| `monitoring` | Valeurs Helm de kube-prometheus-stack |

```bash
kubectl kustomize deploy/k8s/overlays/onprem     # rendu des manifestes
python3 scripts/validate_manifests.py           # contrôles de cohérence
```

Les secrets `stock-secrets` et `nexus-registry` sont créés par le pipeline et ne figurent pas
dans le dépôt. Voir [../docs/05-infrastructure.md](../docs/05-infrastructure.md) et
[../docs/06-pipeline-azure-devops.md](../docs/06-pipeline-azure-devops.md).
