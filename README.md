# Makzeo — Gestion de produits et de stock

> **Makzeo** vient de l'arabe *makhzen* (مخزن, « entrepôt »), le mot qui a donné le français « magasin »
> et l'anglais « magazine ».

Projet de fin d'études : **architecture microservices** + **approche DevOps**
(Docker, Kubernetes, CI/CD Tekton, GitOps FluxCD).

| Dossier | Contenu | Documentation |
|---|---|---|
| [`backend/`](backend) | 5 microservices Spring Boot 4 (Java 21, Clean Architecture) + bibliothèque commune | [README](backend/README.md) · [guide de lancement IntelliJ](backend/LANCEMENT.md) |
| [`frontend/`](frontend) | Application Angular 20 (tableau de bord décisionnel, gestion complète) | [README](frontend/README.md) |
| [`deploy/`](deploy) | Docker Compose, manifests Kubernetes (Kustomize), pipelines Tekton, GitOps FluxCD | [README DevOps](deploy/README.md) |
| [`scripts/`](scripts) | Validation hors-ligne des manifests | — |

## Architecture

```
                    Navigateur (Angular — Makzeo)
                                │
               Ingress Kubernetes / passerelle Nginx  (API Gateway)
     ┌──────────┬──────────────┼───────────────┬──────────────┬────────────┐
     │ /api/auth│ /api/products│ /api/suppliers│ /api/stock   │/api/reports│  /
     ▼  /users  ▼ /categories  ▼               ▼              ▼            ▼
 auth-service  product-service  supplier-service  stock-service  reporting-service  frontend
   (JWT)        (catalogue,       (fournisseurs)   (mouvements,   (PDF/Excel +       (Nginx)
                 codes-barres)                      inventaires,   indicateurs
                                                    alertes)       du dashboard)
     │              │                 │                │
  auth_db      product_db        supplier_db       stock_db        ← PostgreSQL (une base par service)
```

## Démarrage rapide

| Objectif | Commandes |
|---|---|
| **Développer** (IntelliJ + Angular) | lancer les 5 services (voir `backend/LANCEMENT.md`), puis `cd frontend && npm install && npm start` → http://localhost:4200 |
| **Tout lancer avec Docker** | `copy .env.example .env` puis `docker compose up -d --build` → http://localhost:8080 |
| **Données de démonstration** | `python backend/scripts/seed_demo_data.py` (45 jours d'activité simulée pour le dashboard) |
| **Kubernetes local** | `kubectl apply -k deploy/k8s/overlays/dev` (voir `deploy/README.md`) |
| **CI/CD + GitOps** | Tekton (`deploy/tekton`) + FluxCD (`deploy/flux`), voir `deploy/README.md` |

Compte initial : **admin / Admin@12345**.

## Fonctionnalités

- Gestion des produits : références, catégories, **codes-barres EAN-13** (génération, contrôle, étiquette, scan).
- **Entrées / sorties** de stock et **inventaires** (ajustement automatique des écarts).
- **Alertes** de rupture et de seuil minimum : création, escalade et résolution automatiques.
- Gestion des **fournisseurs de tous pays** (pays ISO, identifiant fiscal : ICE, matricule fiscal, n° TVA…).
- **Devise au choix** (MAD, TND, DZD, EUR, USD… toutes les devises ISO 4217), définie par l'administrateur.
- **Historique** complet des mouvements (utilisateur, document, quantités avant / après).
- **Reporting** PDF / Excel et **tableau de bord décisionnel** : état des stocks, réapprovisionnement (couverture, date de rupture estimée), valorisation.
- **Multi-utilisateurs** avec 4 rôles : Administrateur, Gestionnaire, Magasinier, Consultant.
