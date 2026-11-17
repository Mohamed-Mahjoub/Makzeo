# Makzeo — Backend (microservices Spring Boot) : guide de lancement

Ce guide explique pas à pas comment **ouvrir, lancer et tester le backend dans IntelliJ IDEA**
sous Windows.

Le backend contient **5 microservices Spring Boot** et une bibliothèque partagée :

| Module | Rôle | Port | Base PostgreSQL |
|---|---|---|---|
| `auth-service` | Connexion (JWT), utilisateurs, rôles | 8081 | `auth_db` |
| `product-service` | Produits, catégories, codes-barres | 8082 | `product_db` |
| `supplier-service` | Fournisseurs | 8083 | `supplier_db` |
| `stock-service` | Entrées/sorties, historique, inventaires, alertes, tableau de bord | 8084 | `stock_db` |
| `reporting-service` | Rapports PDF / Excel | 8085 | *(aucune)* |
| `common-lib` | Code partagé (sécurité, erreurs, pagination) : **ne se lance pas** | — | — |

Ils ne nécessitent **ni Kafka, ni Eureka, ni API Gateway en local**. Il faut seulement
PostgreSQL. Les appels entre services se font en REST vers `http://localhost:<port>`, déjà
configuré par défaut.

---

## Étape 1 — Prérequis

| Outil | Version | Remarque |
|---|---|---|
| IntelliJ IDEA | Community ou Ultimate | |
| JDK | **21** | Déjà présent sur votre PC (`C:\Users\Mohamed\.jdks\ms-21.0.12`) |
| PostgreSQL | 16 | Via Docker Desktop (Option A) **ou** installé sous Windows (Option B) |
| Postman | Toute version | Déjà installé sur votre PC, sert à tester l'API |
| Connexion Internet | — | Pour le premier téléchargement des dépendances Maven |

Maven n'a pas besoin d'être installé : IntelliJ embarque le sien.

---

## Étape 2 — Ouvrir le projet dans IntelliJ

Le projet est un **projet Maven multi-modules** : il y a un `pom.xml` parent à la racine
qui déclare les 6 modules. On l'ouvre donc **en une seule fois**.

1. `File > Open`, puis sélectionnez le dossier `Documents\stock-management\backend`
   (celui qui contient `pom.xml`, `Dockerfile`, `README.md`…).
2. Cliquez `Open as Project`, puis `Trust Project`.
3. IntelliJ détecte le `pom.xml` parent et importe automatiquement les 6 modules.
   Dans la fenêtre **Maven** (icône `m` à droite), vous devez voir :
   ```
   stock-management-backend (root)
   ├── common-lib
   ├── auth-service
   ├── product-service
   ├── supplier-service
   ├── stock-service
   └── reporting-service
   ```

> Ouvrez bien le dossier `backend` (qui contient le `pom.xml` parent), et non la racine du
> monorepo `stock-management`. Sinon, refermez le projet et recommencez en ciblant le bon dossier.
> Le frontend Angular (`frontend/`) s'ouvre séparément (VS Code ou IntelliJ Ultimate), voir l'Étape 13.

---

## Étape 3 — Configurer le JDK 21

1. `File > Project Structure > Project` :
   - **SDK** : choisissez `ms-21` (ou `Download JDK` → *Temurin 21* s'il n'apparaît pas).
   - **Language level** : `21`.
2. `File > Settings > Build, Execution, Deployment > Build Tools > Maven > Runner` :
   - **JRE** : `ms-21` (important, sinon Maven compile avec un autre JDK).

---

## Étape 4 — Télécharger les dépendances et compiler

1. Dans la fenêtre **Maven**, cliquez sur l'icône 🔄 **Reload All Maven Projects**.
   Le premier chargement prend quelques minutes (barre de progression en bas).
2. Toujours dans la fenêtre Maven : `stock-management-backend (root) > Lifecycle` →
   double-cliquez sur **`install`**.
3. Résultat attendu dans la console :
   ```
   Tests run: 84, Failures: 0, Errors: 0
   BUILD SUCCESS
   ```

Équivalent en ligne de commande (terminal IntelliJ : `Alt+F12`) : `mvn clean install`.

Si une dépendance ne se résout pas, forcez le rafraîchissement : `mvn clean install -U`.

---

## Étape 5 — Lancer PostgreSQL et créer les 4 bases

Chaque microservice possède **sa propre base** (principe *Database per service*).
Les **tables sont créées automatiquement** au démarrage de chaque service par Flyway
(scripts `src/main/resources/db/migration/V1__*.sql`). Vous créez seulement les bases vides.

### Option A — avec Docker Desktop (recommandé)

Dans un terminal PowerShell (ou le terminal IntelliJ) :

```powershell
docker run -d --name stock-postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
```

Attendez ~5 secondes, puis :

```powershell
docker exec -it stock-postgres psql -U postgres -c "CREATE DATABASE auth_db;" -c "CREATE DATABASE product_db;" -c "CREATE DATABASE supplier_db;" -c "CREATE DATABASE stock_db;"
```

Les fois suivantes, il suffit de faire `docker start stock-postgres`.

### Option B — PostgreSQL installé sous Windows

1. Installez PostgreSQL 16 (postgresql.org). Pendant l'installation : mot de passe
   **`postgres`**, port **5432**.
2. Ouvrez **pgAdmin** → serveur local → base `postgres` → `Query Tool`, exécutez :
   ```sql
   CREATE DATABASE auth_db;
   CREATE DATABASE product_db;
   CREATE DATABASE supplier_db;
   CREATE DATABASE stock_db;
   ```

> Si votre mot de passe PostgreSQL n'est pas `postgres`, voir l'Étape 6 point 3.

---

## Étape 6 — Créer les configurations de lancement dans IntelliJ

Chaque service a une classe principale annotée `@SpringBootApplication` :

| Service | Classe à lancer |
|---|---|
| auth-service | `ma.pfe.stock.auth.AuthServiceApplication` |
| product-service | `ma.pfe.stock.product.ProductServiceApplication` |
| supplier-service | `ma.pfe.stock.supplier.SupplierServiceApplication` |
| stock-service | `ma.pfe.stock.stockmgmt.StockServiceApplication` |
| reporting-service | `ma.pfe.stock.reporting.ReportingServiceApplication` |

1. Ouvrez chaque classe (`Shift` `Shift` puis tapez son nom), clic sur ▶ à côté de `main`
   → `Run`. IntelliJ crée la configuration automatiquement.
2. **Ordre conseillé** (attendez `Started ...Application in X seconds` avant le suivant) :
   1. `AuthServiceApplication`
   2. `SupplierServiceApplication`
   3. `ProductServiceApplication`
   4. `StockServiceApplication`
   5. `ReportingServiceApplication`
3. **Mot de passe PostgreSQL différent de `postgres` ?**
   `Run > Edit Configurations` → sélectionnez la configuration → `Modify options >
   Environment variables` → ajoutez `DB_PASSWORD=votreMotDePasse`.
   À faire pour auth, product, supplier et stock (reporting n'a pas de base).

**Astuce IntelliJ** : ouvrez la fenêtre **Services** (`Alt+8`). Les 5 applications Spring
Boot y apparaissent ensemble. Vous pouvez tout démarrer ou arrêter en un clic et lire
chaque console séparément. Vous pouvez aussi créer une **Compound Configuration**
(`Edit Configurations > + > Compound`) qui regroupe les 5 services et les lance d'un seul clic.

### Vérifier que tout est démarré

- Dans la console d'`auth-service`, vous devez voir :
  `Compte administrateur initial 'admin' créé`. Le compte **admin / Admin@12345** est
  créé automatiquement au premier démarrage.
- Dans un navigateur, chaque URL doit renvoyer `{"status":"UP"}` :

| Service | Santé |
|---|---|
| auth-service | http://localhost:8081/actuator/health |
| product-service | http://localhost:8082/actuator/health |
| supplier-service | http://localhost:8083/actuator/health |
| stock-service | http://localhost:8084/actuator/health |
| reporting-service | http://localhost:8085/actuator/health |

---

## Étape 7 — Tester l'API avec Swagger UI

Chaque microservice publie sa documentation **OpenAPI 3** avec une interface **Swagger UI**
(bibliothèque springdoc-openapi). Les 5 services doivent être démarrés.

| Service | Swagger UI | Spécification JSON |
|---|---|---|
| auth-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs |
| product-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs |
| supplier-service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs |
| stock-service | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs |
| reporting-service | http://localhost:8085/swagger-ui.html | http://localhost:8085/v3/api-docs |

### S'authentifier dans Swagger (bouton Authorize)

1. Ouvrez **http://localhost:8081/swagger-ui.html** (auth-service).
2. Dépliez **Authentification → POST /api/auth/login**, puis `Try it out`, et saisissez :
   ```json
   { "username": "admin", "password": "Admin@12345" }
   ```
3. Cliquez `Execute`, puis copiez la valeur de **`accessToken`** dans la réponse, sans les guillemets.
4. Cliquez sur le bouton **Authorize** 🔒 en haut à droite, collez le jeton **sans** le mot `Bearer`,
   puis cliquez `Authorize` et `Close`.
5. Répétez l'étape 4 sur le Swagger de chaque service à tester. Le jeton est valable 8 h et il est
   mémorisé par le navigateur (`persist-authorization`).

### Scénario rapide dans Swagger

| # | Swagger | Opération | Résultat attendu |
|---|---|---|---|
| 1 | 8083 | **Fournisseurs → POST /api/suppliers** `{"code":"F001","name":"Carthage Fournitures","country":"TN","taxId":"1234567/A/M/000"}` | `201`, pays « Tunisie » |
| 2 | 8082 | **Produits → POST /api/products** `{"reference":"PAP-A4","name":"Papier A4","categoryId":2,"minStock":10,"purchasePrice":32.5}` | `201`, code-barres EAN-13 généré |
| 3 | 8084 | **Mouvements de stock → POST /api/stock/movements** `{"productId":1,"type":"IN","quantity":100}` | `201`, stock 0 → 100 |
| 4 | 8084 | idem avec `{"productId":1,"type":"OUT","quantity":95}` | `201`, stock 5, alerte SEUIL_MIN |
| 5 | 8084 | **Alertes → GET /api/stock/alerts** (`activeOnly` = true) | 1 alerte `SEUIL_MIN` |
| 6 | 8084 | sortie de `50` | **409** `INSUFFICIENT_STOCK` |
| 7 | 8085 | **Rapports → GET /api/reports/stock** (`format` = pdf) | lien **Download file** |
| 8 | 8085 | **Tableau de bord → GET /api/reports/analytics** | indicateurs JSON |
| 9 | 8081 | **Paramètres → PUT /api/settings** `{"currency":"TND"}` | `200`, devise = dinar tunisien (3 décimales) ; réservé ADMIN |
| 10 | 8083 | **Fournisseurs → GET /api/suppliers/countries** | liste de tous les pays (ISO 3166) |

> Les identifiants (1, 2…) correspondent à une base neuve. Adaptez-les à vos données au besoin.

## Étape 8 — Importer la collection Postman

1. Ouvrez Postman, cliquez **Import** et sélectionnez `docs\makzeo.postman_collection.json`.
2. La collection **« Makzeo - API backend (PFE) »** apparaît avec 8 dossiers.
3. Vous n'avez **rien à copier-coller** :
   - **Login admin** enregistre le jeton JWT dans la variable `{{token}}`, utilisée par les autres requêtes ;
   - les requêtes de création enregistrent les identifiants (`supplierId`, `productId`, `inventoryId`…).
4. Les variables `authUrl`, `productUrl`, `supplierUrl`, `stockUrl` et `reportingUrl` pointent vers les
   ports 8081 à 8085. Avec Docker Compose, mettez-les toutes à `http://localhost:8080`.

> **Alternative IntelliJ** : ouvrez `docs\api-requests.http` et cliquez ▶ à côté de chaque requête,
> dans l'ordre (HTTP Client d'IntelliJ **Ultimate**).

> Swagger UI (Étape 7) permet aussi de tester chaque route directement depuis le navigateur.

## Étape 9 — Scénario de test recommandé (dans Postman)

Le plus simple : clic droit sur la collection → **Run collection** → **Run**. Les 65 requêtes
s'exécutent dans l'ordre et chacune vérifie son code de retour. Vous pouvez aussi les lancer une par une :

| Dossier | Ce que vous vérifiez |
|---|---|
| 1. Authentification | Connexion, profil, création d'un gestionnaire, d'un magasinier et d'un consultant |
| 2. Paramètres (devise) | Devise actuelle, liste des devises ISO, passage au **dinar tunisien (TND, 3 décimales)** |
| 3. Fournisseurs (tous pays) | Liste des pays, fournisseurs **marocain (ICE)**, **tunisien (matricule fiscal)** et **français (n° TVA)**, filtre par pays |
| 4. Catalogue | Produit avec **code-barres EAN-13 généré** et prix à 3 décimales (`32,505`), scan, recherche |
| 5. Mouvements & alertes | Entrée 100, sortie 92 (alerte **SEUIL_MIN**), sortie 8 (**RUPTURE**), acquittement, réapprovisionnement (alerte résolue) |
| 6. Inventaire | Comptage avec écart, validation, mouvement **ADJUSTMENT** |
| 7. Rapports & tableau de bord | PDF et Excel (**Send and Download**) avec les montants en TND, indicateurs du tableau de bord |
| 8. Cas d'erreur | 401, 403 (rôles, devise), 409 (stock insuffisant, doublons), 400 (validation, EAN-13, ICE), puis retour au MAD |

### Tester les rôles (multi-utilisateurs)

Les requêtes du dossier 8 utilisent déjà le jeton du magasinier et du consultant. Pour tester à la main :
1. Dans **Login admin**, remplacez le corps par le compte magasinier créé au dossier 1
   (`magasinier<nombre>` / `Password123`) et faites **Send**.
2. **Créer un fournisseur** renvoie alors **403** (`ACCESS_DENIED`), alors qu'une entrée de stock fonctionne.
3. Refaites **Login admin** avec `admin / Admin@12345` pour revenir en administrateur.

Matrice complète des droits : `README.md`, section 2.

## Étape 10 — Test automatique complet (optionnel)

Si Python 3 est installé, avec les 5 services démarrés, dans le terminal IntelliJ :

```powershell
python scripts\e2e_test.py
```

Le script rejoue automatiquement tout le scénario (utilisateurs, rôles, catalogue, stock,
alertes, inventaire, rapports). Il utilise des noms uniques, donc il peut être relancé autant
de fois que voulu. Résultat attendu :

```
68 vérifications réussies.
```

Option : `--save-reports C:\temp` enregistre les 8 rapports générés dans ce dossier.

---

## Étape 11 — Lancer les tests automatisés depuis IntelliJ

- **Tous les tests** : fenêtre Maven → `stock-management-backend (root) > Lifecycle > test`
  → **84 tests**.
- **Un seul service** : clic droit sur `stock-service/src/test/java` → `Run 'All Tests'`.
- **Aucun Docker ni PostgreSQL requis** pour ces tests : ce sont des tests unitaires
  (domaine, cas d'utilisation) et des tests web/sécurité (`@WebMvcTest`).

| Module | Tests |
|---|---|
| common-lib | `PageResultTest` |
| auth-service | `UserTest`, `AuthServiceTest`, `UserServiceTest`, `SettingsServiceTest` (devises ISO 4217), `UserControllerSecurityTest` (401/403/400) |
| product-service | `BarcodeTest` (EAN-13), `ProductTest`, `ProductServiceTest`, `ProductControllerTest` |
| supplier-service | `SupplierTest` (pays ISO 3166, ICE / identifiant fiscal), `SupplierServiceTest` |
| stock-service | `StockItemTest`, `InventoryTest`, `StockMovementServiceTest` (scénario complet alertes) |
| reporting-service | `ReportFormatTest`, `ReportServiceTest` (génère de vrais PDF et XLSX), `AnalyticsServiceTest` (indicateurs du dashboard) |

---

## Étape 12 — Charger des données de démonstration (pour le tableau de bord)

Le tableau de bord (frontend) affiche des courbes sur 7 à 180 jours. Pour qu'il soit parlant,
le script suivant crée 4 fournisseurs, 24 produits et **45 jours d'activité simulée**
(entrées, sorties, ruptures, réapprovisionnements). Les 5 services doivent être démarrés,
de préférence sur une base neuve :

```powershell
python scripts\seed_demo_data.py
```

À la fin, le script antidate les mouvements dans `stock_db`. Par défaut, il utilise
`docker exec -i stock-postgres psql …` (Option A de l'Étape 5). Avec PostgreSQL installé sous
Windows, passez la commande `psql` (ou exécutez dans pgAdmin la requête SQL affichée par le script) :

```powershell
python scripts\seed_demo_data.py --sql-cmd "psql -h localhost -U postgres -d stock_db"
```

## Étape 13 — Lancer le frontend Angular

Pré-requis : **Node.js 22** (https://nodejs.org). Les 5 services restent lancés dans IntelliJ.

```powershell
cd ..\frontend
npm install        # 1re fois uniquement
npm start          # http://localhost:4200
```

`npm start` utilise `proxy.conf.json` : chaque appel `/api/...` est redirigé vers le bon port
(8081 à 8085). Connectez-vous avec `admin` / `Admin@12345`, puis ouvrez le **Tableau de bord**
(onglets *État des stocks*, *Réapprovisionnement*, *Valorisation*). Détails : `frontend/README.md`.

## Étape 14 — Alternative : tout lancer avec Docker Compose

Pour lancer **toute l'application** sans IntelliJ (PostgreSQL + 5 services + frontend +
passerelle Nginx sur le port 8080), **depuis la racine du monorepo** :

```powershell
cd ..
copy .env.example .env
docker compose up -d --build
```

Arrêtez d'abord les services lancés dans IntelliJ et le conteneur `stock-postgres`, car les
ports sont les mêmes. Le premier build prend plusieurs minutes. L'application est alors sur
**http://localhost:8080**, et le test automatique se lance avec :

```powershell
python backend\scripts\e2e_test.py --gateway http://localhost:8080
```

Pour Kubernetes, Tekton et FluxCD : voir `deploy/README.md`.

## Dépannage rapide

| Symptôme | Cause probable | Solution |
|---|---|---|
| `password authentication failed for user "postgres"` | Mot de passe PostgreSQL différent de `postgres` | Variable `DB_PASSWORD` dans la configuration de lancement (Étape 6.3) |
| `database "auth_db" does not exist` | Bases non créées | Revoir l'Étape 5 |
| `Connection refused` … `5432` | PostgreSQL arrêté | `docker start stock-postgres` ou démarrer le service Windows *postgresql-x64-16* |
| `Port 8081 was already in use` | Service déjà lancé (ou autre programme sur ce port) | Vérifier la fenêtre Services (`Alt+8`), arrêter le doublon |
| `release version 21 not supported` / code en rouge | Mauvais JDK | Revoir l'Étape 3, puis Maven 🔄 Reload |
| `Cannot resolve symbol` sur les classes de `common-lib` | Import Maven incomplet | Maven 🔄 Reload, puis `Lifecycle > install` sur la racine |
| Swagger : `401` sur une opération | Bouton **Authorize** non renseigné sur ce service (le jeton se colle sur chaque Swagger) | Étape 7 |
| `401 Unauthorized` dans Postman | Jeton absent ou expiré (validité 8 h) | Relancer **Login admin** |
| `403 Forbidden` | Le rôle connecté n'a pas le droit | Normal (sécurité par rôle) : se connecter en admin |
| `503` à la création d'un produit avec fournisseur | `supplier-service` non démarré | Lancer `SupplierServiceApplication` |
| `503` sur un mouvement de stock ou un rapport | `product-service` ou `stock-service` non démarré | Vérifier que les 5 services sont `UP` |
| `409 Conflict` à la création | Référence / code / email déjà existant | Normal : changer la valeur (voir « Relancer le scénario ») |
| Message `Mockito is currently self-attaching…` pendant les tests | Avertissement du JDK 21 | Sans conséquence, les tests passent |
