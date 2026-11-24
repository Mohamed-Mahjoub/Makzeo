# Makzeo — Gestion des produits et des stocks

Makzeo est une application web de gestion des produits (références, catégories, codes-barres),
des fournisseurs et des stocks, construite sur une architecture microservices et livrée par une
chaîne DevOps complète (Azure DevOps, SonarQube, Nexus, Docker, Kubernetes, Prometheus, Grafana).

| Domaine | Fonctionnalités |
|---|---|
| Authentification | Connexion JWT, rôles (ADMIN, GESTIONNAIRE, MAGASINIER, CONSULTANT), gestion des utilisateurs |
| Produits | Catalogue, catégories, référence unique, code-barres, prix, seuils d'alerte, activation |
| Fournisseurs | Fiches fournisseurs, pays, rattachement aux produits |
| Stocks | Entrées, sorties, ajustements, niveaux par produit, alertes de rupture, inventaires |
| Reporting | Tableau de bord, état des stocks, historique des mouvements, rapports d'inventaire, exports |
| Achats IA | Veille mensuelle des fournisseurs tunisiens, demandes de prix validées puis envoyées par e-mail, comparaison des devis, prédiction des prix, achats anticipés, économies et ROI |

## Architecture

```
                    Navigateur
                        │  HTTP
              ┌─────────▼──────────┐
              │  Ingress NGINX     │  (Docker : passerelle Nginx :8080)
              └─────────┬──────────┘
        ┌───────────────┼──────────────────────────────────────────┐
        │ /             │ /api/auth, /api/users  /api/products ...  │
 ┌──────▼─────┐  ┌──────▼──────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐
 │ frontend   │  │auth-service │ │product-svc │ │supplier-svc│ │stock-svc   │ │reporting-svc │
 │ Angular 20 │  │   :8081     │ │   :8082    │ │   :8083    │ │   :8084    │ │   :8085      │
 └────────────┘  └──────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └──────┬───────┘
                        └──────────────┴──── PostgreSQL 16 ─┴──────────────┘   (agrège les autres)
                          auth_db   product_db   supplier_db   stock_db

 procurement-service :8086 (module IA achats) ─► procurement_db, API Claude, SMTP (Mailpit)
```

- **Backend** : Java 21, Spring Boot 4, Spring Security (JWT HMAC), Spring Data JPA, Flyway,
  springdoc-openapi, Actuator + Micrometer (Prometheus). Architecture propre (domaine / application / infrastructure).
- **Frontend** : Angular 20 (composants autonomes, signals), servi par Nginx non privilégié.
- **Données** : PostgreSQL 16, une base par microservice, schémas versionnés par Flyway.

## Démarrage rapide (poste de développement)

Prérequis : Docker Desktop (ou Docker Engine + Compose v2).

```bash
cp .env.example .env          # puis renseigner POSTGRES_PASSWORD, JWT_SECRET, ADMIN_PASSWORD
docker compose up -d --build
```

Application : <http://localhost:8080> — compte `admin` / valeur de `ADMIN_PASSWORD`.

## Chaîne DevOps

```
 git push ─► Azure DevOps (agent auto-hébergé sur le master)
              ├─ Build      Maven : tests JUnit + JaCoCo ─► SonarQube (Quality Gate) ─► Nexus (maven)
              │             Angular : build prod ─► SonarQube ─► Trivy (dépendances)
              │             Contrôle des manifestes Kubernetes
              ├─ Images     7 images Docker ─► Trivy ─► Nexus (registre Docker)
              ├─ Deploy     Kustomize ─► kubectl apply ─► cluster kubeadm (1 master + 2 workers, VMware)
              ├─ Smoke      test de bout en bout via l'Ingress
              └─ Monitoring Prometheus : collecte des 6 services, alertes critiques ─► Grafana
```

## Structure du dépôt

| Dossier | Contenu |
|---|---|
| `backend/` | Projet Maven multi-modules : `common-lib` et les 6 microservices |
| `frontend/` | Application Angular, configuration Nginx et en-têtes de sécurité |
| `deploy/docker/` | Passerelle Nginx et initialisation PostgreSQL pour Docker Compose |
| `deploy/k8s/` | Manifestes Kubernetes : `base/` et overlay `onprem/` (HPA, PDB, supervision) |
| `deploy/monitoring/` | Valeurs Helm de kube-prometheus-stack |
| `infra/vmware/` | Scripts de création du cluster kubeadm |
| `infra/devops-vm/` | SonarQube, Nexus, outils de build et agent Azure Pipelines |
| `ci/` | Paramètres Maven du pipeline et contrôles post-déploiement |
| `scripts/` | Validation des manifestes et du pipeline |
| `docs/` | Documentation technique |
| `azure-pipelines.yml` | Pipeline CI/CD |

## Documentation

1. [Architecture](docs/01-architecture.md)
2. [API REST](docs/02-api.md)
3. [Sécurité](docs/03-securite.md)
4. [Exploitation locale](docs/04-exploitation-locale.md)
5. [Infrastructure VMware et Kubernetes](docs/05-infrastructure.md)
6. [Pipeline Azure DevOps](docs/06-pipeline-azure-devops.md)
7. [Supervision Prometheus / Grafana](docs/07-supervision.md)
8. [Dépannage](docs/08-depannage.md)
9. [Module IA achats](docs/09-module-ia.md)
10. [Démonstration au jury](docs/10-demo-jury.md)
