# Makzeo — Backend microservices

Backend de **Makzeo**, l'application de **gestion de produits et de stock** (PFE) : architecture **microservices**, **Clean Architecture** (3 couches) et approche **DevOps** (Docker, Kubernetes, Tekton, FluxCD).

| Élément | Choix |
|---|---|
| Langage / Framework | Java 21, Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7) |
| Base de données | PostgreSQL 16 — une base par microservice, migrations **Flyway** |
| Sécurité | JWT HS256 émis par `auth-service`, chaque service est un *OAuth2 Resource Server* |
| Documentation API | OpenAPI 3 + Swagger UI (springdoc-openapi 3.1) |
| Communication | REST synchrone (`RestClient`) avec relais du jeton utilisateur |
| Rapports | PDF (OpenPDF) et Excel (Apache POI) |
| Build | Maven multi-modules |

## 1. Architecture

```
                         ┌──────────────────────────────┐
  Angular (4200) ──────► │ Passerelle API                │  Nginx (docker compose) / Ingress (Kubernetes)
                         └──┬──────┬──────┬──────┬──────┬┘
                            │      │      │      │      │
                   /api/auth│      │      │      │      │/api/reports
                  /api/users│      │      │      │      │
                  ┌─────────▼─┐ ┌──▼──────────┐ ┌▼─────────────┐ ┌▼──────────────┐ ┌▼──────────────────┐
                  │auth-service│ │product-svc  │ │supplier-svc  │ │stock-service  │ │reporting-service  │
                  │   :8081    │ │   :8082     │ │   :8083      │ │   :8084       │ │   :8085 (sans BD) │
                  └─────┬──────┘ └──┬───────┬──┘ └──────┬───────┘ └──┬─────────┬──┘ └──┬──────────┬─────┘
                        │           │       └─vérifie──►│            │         │       │          │
                        │           │        fournisseur│            │◄─produit┘       │          │
                        │           │◄───────────────────────────────┼──────── lit ────┘          │
                        │           │                   │            │◄────────── lit ────────────┘
                   ┌────▼───┐  ┌────▼─────┐      ┌──────▼────┐  ┌────▼────┐
                   │auth_db │  │product_db│      │supplier_db│  │stock_db │   PostgreSQL
                   └────────┘  └──────────┘      └───────────┘  └─────────┘
```

| Microservice | Responsabilités | Port | Base |
|---|---|---|---|
| `auth-service` | Connexion (JWT), utilisateurs, rôles, mots de passe, **paramètres (devise)** | 8081 | `auth_db` |
| `product-service` | Produits, catégories, références, **codes-barres EAN-13** (génération + contrôle de clé) | 8082 | `product_db` |
| `supplier-service` | Fournisseurs de **tous pays** (code, pays ISO 3166, identifiant fiscal, contacts) | 8083 | `supplier_db` |
| `stock-service` | Entrées / sorties, niveaux de stock, **historique**, **inventaires**, **alertes** (rupture / seuil minimum), tableau de bord | 8084 | `stock_db` |
| `reporting-service` | Rapports **PDF / Excel** : état du stock valorisé, mouvements, alertes, PV d'inventaire | 8085 | — |
| `common-lib` | Noyau partagé : exceptions métier, format d'erreur, sécurité JWT, pagination, client REST | — | — |

### Clean Architecture (dans chaque service)

```
ma.pfe.stock.<service>
├── domain            ← cœur métier, Java pur (entités, objets-valeurs, règles, ports de persistance)
│   ├── model
│   ├── port          (interfaces : ProductRepository, StockItemRepository…)
│   └── service       (services de domaine : AlertPolicy…)
├── application       ← cas d'utilisation (orchestration, transactions)
│   ├── dto           (commandes et vues)
│   ├── port          (interfaces vers l'extérieur : ProductCatalog, TokenProvider, ReportRenderer…)
│   └── service
└── infrastructure    ← détails techniques (adaptateurs)
    ├── persistence   (entités JPA, Spring Data, adaptateurs des ports)
    ├── client        (appels REST vers les autres microservices)
    ├── web           (contrôleurs REST, validation des requêtes)
    └── config
```

Règle de dépendance : `infrastructure → application → domain`. Le domaine ne dépend d'aucun framework (les entités JPA sont séparées des entités métier).

## 2. Rôles et droits

| Action | ADMIN | GESTIONNAIRE | MAGASINIER | CONSULTANT |
|---|:-:|:-:|:-:|:-:|
| Gérer les utilisateurs | ✔ | | | |
| Consulter produits / fournisseurs / stock / alertes | ✔ | ✔ | ✔ | ✔ |
| Créer / modifier produits, catégories, fournisseurs | ✔ | ✔ | | |
| Entrées / sorties de stock, saisie d'inventaire, acquitter une alerte | ✔ | ✔ | ✔ | |
| Valider / annuler un inventaire, recalculer les alertes | ✔ | ✔ | | |
| Générer les rapports PDF / Excel | ✔ | ✔ | | ✔ |

Un compte `admin` est créé au premier démarrage (mot de passe = variable `ADMIN_PASSWORD`, défaut `Admin@12345`).

## 3. API REST (via la passerelle `http://localhost:8080`)

Toutes les routes (sauf `POST /api/auth/login` et `/actuator/health`) exigent l'en-tête `Authorization: Bearer <jeton>`.

| Méthode | Route | Description |
|---|---|---|
| POST | `/api/auth/login` | Connexion → `accessToken` |
| GET | `/api/auth/me` | Utilisateur connecté |
| PUT | `/api/auth/me/password` | Changer son mot de passe |
| GET/POST | `/api/users` | Lister (`q`, `page`, `size`) / créer un utilisateur |
| GET/PUT | `/api/users/{id}` | Détail / modification (email, nom, rôles) |
| PATCH | `/api/users/{id}/enable` · `/disable` | Activer / désactiver |
| PUT | `/api/users/{id}/password` | Réinitialiser le mot de passe |
| GET | `/api/settings` | Devise de l'application (code, nom, symbole, décimales) |
| PUT | `/api/settings` | Changer la devise `{"currency":"TND"}` — ADMIN |
| GET | `/api/settings/currencies` | Devises ISO 4217 en vigueur (les plus courantes en premier) |
| GET/POST | `/api/categories` | Catégories |
| PUT/DELETE | `/api/categories/{id}` | Modifier / supprimer (refus si produits rattachés) |
| GET | `/api/products?q=&categoryId=&supplierId=&active=&page=&size=` | Recherche multicritère |
| POST | `/api/products` | Créer (code-barres EAN-13 généré si absent) |
| GET/PUT | `/api/products/{id}` | Détail / modification |
| GET | `/api/products/reference/{ref}` · `/barcode/{code}` | Recherche par référence / **scan code-barres** |
| GET | `/api/products/batch?ids=1,2` | Lecture groupée (inter-services) |
| PATCH | `/api/products/{id}/activate` · `/deactivate` | Activer / désactiver (pas de suppression physique) |
| GET/POST | `/api/suppliers?q=&country=&active=` | Fournisseurs (filtre par pays ISO, ex. `TN`) |
| GET | `/api/suppliers/countries` | Tous les pays (ISO 3166-1, noms en français) |
| GET/PUT | `/api/suppliers/{id}` · PATCH `/activate` `/deactivate` | |
| POST | `/api/stock/movements` | Entrée (`IN`) ou sortie (`OUT`) |
| GET | `/api/stock/movements?productId=&type=&from=&to=&user=&supplierId=&inventoryId=` | **Historique** (dates `AAAA-MM-JJ`) |
| GET | `/api/stock/levels?q=&status=OK\|LOW\|OUT_OF_STOCK` | Niveaux de stock |
| GET | `/api/stock/levels/{productId}` · `/levels/batch?productIds=` | |
| GET | `/api/stock/dashboard` | Indicateurs (ruptures, alertes, mouvements du jour…) |
| GET | `/api/stock/alerts?status=&type=&activeOnly=true` | **Alertes** |
| PATCH | `/api/stock/alerts/{id}/acknowledge` | Prendre en compte une alerte |
| POST | `/api/stock/alerts/recompute` | Réévaluer après changement de seuils |
| GET/POST | `/api/stock/inventories` | **Inventaires** |
| GET | `/api/stock/inventories/{id}` | Détail avec lignes et écarts |
| PUT | `/api/stock/inventories/{id}/counts` | Saisir un comptage `{productId, countedQuantity}` |
| DELETE | `/api/stock/inventories/{id}/counts/{productId}` | Retirer un comptage |
| POST | `/api/stock/inventories/{id}/validate` · `/cancel` | Valider (ajustements automatiques) / annuler |
| GET | `/api/reports/stock?format=pdf\|xlsx&categoryId=` | État du stock valorisé |
| GET | `/api/reports/movements?format=&from=&to=&type=&productId=` | Historique des mouvements |
| GET | `/api/reports/alerts?format=&activeOnly=` | Alertes |
| GET | `/api/reports/inventories/{id}?format=` | Procès-verbal d'inventaire |
| GET | `/api/reports/analytics?days=30&categoryId=` | Indicateurs du tableau de bord (JSON) : KPI, flux quotidiens, valorisation, réapprovisionnement — tous les rôles |

Format d'erreur unique :

```json
{ "timestamp": "...", "status": 409, "error": "Conflict", "code": "INSUFFICIENT_STOCK",
  "message": "Stock insuffisant pour PRD-1 : disponible 3, demandé 5", "path": "/api/stock/movements", "violations": [] }
```

**Swagger UI** (OpenAPI 3, springdoc) : `http://localhost:<port>/swagger-ui.html` sur chaque service
(8081 à 8085), spécification JSON sur `/v3/api-docs`. Le bouton **Authorize** accepte le jeton obtenu via `POST /api/auth/login`.

Des exemples prêts à l'emploi : collection Postman [`docs/makzeo.postman_collection.json`](docs/makzeo.postman_collection.json) (65 requêtes) et [`docs/api-requests.http`](docs/api-requests.http) (client HTTP d'IntelliJ).

### Règles métier principales
- **Alertes** : stock = 0 → `RUPTURE` ; 0 < stock ≤ seuil minimum → `SEUIL_MIN`. Création, escalade et **résolution automatique** à chaque mouvement ; pas de doublon.
- **Sortie** refusée si stock insuffisant (409). Mouvements refusés sur un produit désactivé.
- **Concurrence** : verrou pessimiste (`SELECT … FOR UPDATE`) par produit → aucune mise à jour perdue ; création idempotente de la ligne de stock (`ON CONFLICT DO NOTHING`).
- **Inventaire** : la quantité théorique est figée au comptage ; la validation aligne le stock et crée un mouvement `ADJUSTMENT` par écart (traçabilité complète).
- **Historique** immuable : chaque mouvement conserve quantité avant/après, utilisateur, document, date.
- **Devise** : choisie par l'administrateur (toutes les devises ISO 4217 : MAD, TND, DZD, EUR, USD…), appliquée à tous les écrans, rapports et indicateurs. Pas de conversion : les prix sont saisis dans cette devise, avec jusqu'à 3 décimales (millimes du dinar tunisien).
- **Fournisseurs de tous pays** : pays au format ISO 3166-1 alpha-2 et identifiant fiscal générique (ICE à 15 chiffres contrôlé pour le Maroc, matricule fiscal, n° TVA, EIN…), unique au sein d'un même pays.
- **Code-barres** : EAN-13 (clé de contrôle vérifiée) ou code interne Code 128 ; génération automatique d'un EAN-13 interne (préfixe GS1 `200`).

## 4. Démarrage

### Option A — Docker Compose (recommandé)
Prérequis : Docker Desktop. Le fichier `docker-compose.yml` est **à la racine du monorepo** (il lance aussi le frontend).
```bash
cd ..                          # racine du monorepo
cp .env.example .env           # adapter JWT_SECRET et ADMIN_PASSWORD
docker compose up -d --build   # 1er build : quelques minutes (téléchargement des dépendances Maven / npm)
python3 backend/scripts/e2e_test.py --gateway http://localhost:8080   # scénario complet de vérification
```

### Option B — En local (IntelliJ / VS Code)
Prérequis : JDK 21, Maven 3.9+, PostgreSQL 16 avec les bases `auth_db`, `product_db`, `supplier_db`, `stock_db` (script `deploy/docker/postgres/init-databases.sql`), utilisateur `postgres/postgres`.
```bash
mvn clean install                       # compile + 84 tests unitaires / web
mvn -pl auth-service spring-boot:run    # puis product-, supplier-, stock-, reporting-service (un terminal chacun)
python3 scripts/e2e_test.py             # ports 8081..8085 en direct
```

### Variables d'environnement

| Variable | Défaut | Services |
|---|---|---|
| `JWT_SECRET` | valeur de dev (à changer !) | tous |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `<service>_db` / `postgres` / `postgres` | auth, product, supplier, stock |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | `admin` / `Admin@12345` / `admin@stock.local` | auth |
| `JWT_VALIDITY` | `8h` | auth |
| `SUPPLIER_SERVICE_URL` · `SUPPLIER_CHECK_ENABLED` | `http://localhost:8083` · `true` | product |
| `PRODUCT_SERVICE_URL` | `http://localhost:8082` | stock, reporting |
| `STOCK_SERVICE_URL` | `http://localhost:8084` | reporting |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | tous |
| `APP_ZONE` | `Africa/Casablanca` | stock, reporting |

### Sondes (Kubernetes)
Chaque service expose `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/metrics`.

## 5. Tests

| Niveau | Contenu |
|---|---|
| Unitaires domaine | `Barcode` (EAN-13), `Product`, `User`, `Supplier`, `StockItem`, `AlertPolicy`, `Inventory` |
| Unitaires application | `AuthService`, `UserService`, `SettingsService`, `ProductService`, `SupplierService`, scénario complet `StockMovementService` + alertes, `ReportService` (génère de vrais PDF / XLSX), `AnalyticsService` (indicateurs du dashboard) |
| Web + sécurité (`@WebMvcTest`) | 401 / 403 / 400 / 404, validation, format d'erreur |
| E2E (`scripts/e2e_test.py`) | 68 vérifications sur les 5 services démarrés avec PostgreSQL |

## 6. Structure
```
backend/
├── pom.xml                  (parent Maven)
├── common-lib/              auth-service/   product-service/
├── supplier-service/        stock-service/  reporting-service/
├── Dockerfile               (unique, --build-arg SERVICE=<service>)
├── docs/                    collection Postman (makzeo.postman_collection.json) et requêtes HTTP IntelliJ
└── scripts/                 e2e_test.py (68 vérifications), seed_demo_data.py (données de démo)
```
Docker Compose, Kubernetes, Tekton et FluxCD : voir le dossier `deploy/` à la racine du monorepo.
