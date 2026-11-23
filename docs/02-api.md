# 2. API REST

Toutes les routes sont préfixées par `/api` et accessibles via le point d'entrée unique
(passerelle ou Ingress). Hormis `POST /api/auth/login`, elles exigent l'en-tête
`Authorization: Bearer <jeton>`.

La documentation interactive OpenAPI (Swagger UI) est disponible en environnement de
développement sur chaque service : `http://localhost:<port>/swagger-ui.html`. Elle est désactivée
dans Kubernetes (`SWAGGER_ENABLED=false`).

Des requêtes prêtes à l'emploi sont fournies dans `backend/docs/api-requests.http` et
`backend/docs/makzeo.postman_collection.json`.

## 2.1 Rôles

| Rôle | Droits principaux |
|---|---|
| ADMIN | Tout, y compris la gestion des utilisateurs et des paramètres |
| GESTIONNAIRE | Catalogue, fournisseurs, mouvements, inventaires |
| MAGASINIER | Consultation, mouvements de stock, saisie des comptages d'inventaire |
| CONSULTANT | Consultation et rapports |

## 2.2 Authentification et utilisateurs — auth-service (8081)

| Méthode | Route | Description |
|---|---|---|
| POST | `/api/auth/login` | Connexion ; renvoie le jeton JWT, sa date d'expiration et le profil |
| GET | `/api/auth/me` | Profil de l'utilisateur connecté |
| PUT | `/api/auth/me/password` | Changement de son propre mot de passe |
| GET / POST | `/api/users` | Liste paginée / création d'un utilisateur (ADMIN) |
| GET / PUT | `/api/users/{id}` | Consultation / modification |
| PATCH | `/api/users/{id}/enable`, `/disable` | Activation / désactivation du compte |
| PUT | `/api/users/{id}/password` | Réinitialisation du mot de passe (ADMIN) |
| GET / PUT | `/api/settings` | Paramètres de l'application (devise) |
| GET | `/api/settings/currencies` | Devises disponibles |

## 2.3 Catalogue — product-service (8082)

| Méthode | Route | Description |
|---|---|---|
| GET / POST | `/api/categories` | Liste / création |
| GET / PUT / DELETE | `/api/categories/{id}` | Consultation / modification / suppression (refusée si utilisée) |
| GET / POST | `/api/products` | Recherche paginée (`q`, catégorie, statut) / création |
| GET / PUT | `/api/products/{id}` | Consultation / modification |
| GET | `/api/products/reference/{reference}` | Recherche par référence |
| GET | `/api/products/barcode/{barcode}` | Recherche par code-barres (lecture douchette) |
| GET | `/api/products/batch?ids=` | Lecture groupée (appels inter-services) |
| PATCH | `/api/products/{id}/activate`, `/deactivate` | Activation / désactivation |

## 2.4 Fournisseurs — supplier-service (8083)

| Méthode | Route | Description |
|---|---|---|
| GET / POST | `/api/suppliers` | Recherche paginée (`q`, `country`) / création |
| GET / PUT | `/api/suppliers/{id}` | Consultation / modification |
| PATCH | `/api/suppliers/{id}/activate`, `/deactivate` | Activation / désactivation |
| GET | `/api/suppliers/countries` | Pays disponibles |
| GET | `/api/suppliers/batch?ids=` | Lecture groupée |

## 2.5 Stocks — stock-service (8084)

| Méthode | Route | Description |
|---|---|---|
| GET / POST | `/api/stock/movements` | Historique filtrable / enregistrement d'une entrée, sortie ou ajustement |
| GET | `/api/stock/levels`, `/levels/{productId}`, `/levels/batch` | Niveaux de stock |
| GET | `/api/stock/dashboard` | Indicateurs du tableau de bord |
| GET | `/api/stock/alerts` | Alertes (rupture, seuil bas) |
| PATCH | `/api/stock/alerts/{id}/acknowledge` | Prise en compte d'une alerte |
| POST | `/api/stock/alerts/recompute` | Recalcul des alertes |
| GET / POST | `/api/stock/inventories` | Liste / ouverture d'un inventaire |
| GET | `/api/stock/inventories/{id}` | Détail |
| PUT / DELETE | `/api/stock/inventories/{id}/counts[/{productId}]` | Saisie / suppression d'un comptage |
| POST | `/api/stock/inventories/{id}/validate`, `/cancel` | Validation (génère les ajustements) / annulation |

## 2.6 Reporting — reporting-service (8085)

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/reports/stock` | État des stocks valorisé |
| GET | `/api/reports/movements` | Rapport des mouvements sur une période |
| GET | `/api/reports/alerts` | Rapport des alertes |
| GET | `/api/reports/inventories/{id}` | Rapport d'inventaire |
| GET | `/api/reports/analytics?days=` | Indicateurs et tendances |

## 2.7 Format des erreurs

Toutes les erreurs suivent le même format JSON (`ApiError`), avec un code stable exploitable par
l'interface (`VALIDATION_ERROR`, `NOT_FOUND`, `ACCESS_DENIED`, `DATA_INTEGRITY`, ...) :

```json
{
  "timestamp": "2026-05-12T09:41:27Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Données invalides",
  "path": "/api/products",
  "violations": [ { "field": "reference", "message": "ne doit pas être vide" } ]
}
```

| Code | Cas |
|---|---|
| 400 | Données invalides (validation Bean Validation, règle métier) |
| 401 | Jeton absent, invalide ou expiré ; identifiants incorrects |
| 403 | Rôle insuffisant |
| 404 | Ressource inexistante |
| 409 | Doublon (référence, code-barres, code fournisseur, nom d'utilisateur) |
| 429 | Trop de tentatives de connexion |
| 503 | Service distant indisponible (`REMOTE_SERVICE_ERROR`) |

## 2.8 Points techniques

| Route | Usage |
|---|---|
| `/actuator/health/liveness`, `/readiness` | Sondes Kubernetes |
| `/actuator/prometheus` | Métriques collectées par Prometheus (non exposée par l'Ingress ni par la passerelle) |
