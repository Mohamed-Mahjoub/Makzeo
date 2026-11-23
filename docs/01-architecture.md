# 1. Architecture

## 1.1 Vue d'ensemble

Makzeo est découpée en six microservices Spring Boot indépendants, une interface Angular et une
base PostgreSQL. Chaque service possède sa propre base de données et expose une API REST
documentée en OpenAPI 3. L'interface n'appelle jamais les services directement : toutes les
requêtes passent par un point d'entrée unique (passerelle Nginx en local, Ingress NGINX dans
Kubernetes) qui route selon le préfixe d'URL.

| Service | Port | Base | Responsabilité |
|---|---|---|---|
| auth-service | 8081 | auth_db | Comptes, rôles, émission des jetons JWT, paramètres (devises) |
| product-service | 8082 | product_db | Produits, catégories, références, codes-barres |
| supplier-service | 8083 | supplier_db | Fournisseurs, pays |
| stock-service | 8084 | stock_db | Mouvements, niveaux, alertes, inventaires |
| reporting-service | 8085 | — | Tableaux de bord et rapports (agrège les autres services) |
| procurement-service | 8086 | procurement_db | Module IA achats : veille fournisseurs, RFQ, devis, prédiction des prix, recommandations ([chapitre 9](09-module-ia.md)) |
| frontend | 8080 | — | Interface Angular servie par Nginx |

## 1.2 Organisation du code (Clean Architecture)

Chaque microservice suit la même organisation en couches, les dépendances pointant toujours
vers le domaine :

```
ma.pfe.stock.<service>
├── domain/            modèle métier pur (entités, règles, exceptions), ports de sortie
├── application/       cas d'utilisation (services), DTO, ports (ex. PasswordHasher)
└── infrastructure/
    ├── persistence/   adaptateurs JPA (entités, repositories, mapping)
    ├── web/           contrôleurs REST, gestion des erreurs
    ├── security/      implémentations techniques (BCrypt, JWT)
    └── config/        configuration Spring
```

Le module `common-lib` mutualise ce qui est transverse : filtre de sécurité JWT, configuration
CORS, format JSON uniforme des erreurs (`ApiError`, `GlobalExceptionHandler`), clients REST
inter-services, pagination, configuration OpenAPI et dépendances de supervision.

## 1.3 Communication entre services

- **Synchrone, REST** : `product-service` vérifie l'existence des fournisseurs auprès de
  `supplier-service` ; `stock-service` interroge `product-service` ; `reporting-service`
  agrège `auth-service`, `product-service` et `stock-service`.
- **Propagation de l'identité** : le jeton JWT de l'utilisateur est transmis lors des appels
  inter-services (`BearerTokenRelayInterceptor`) ; chaque service le valide avec la clé partagée `JWT_SECRET`.
- **Appels groupés** (`/batch`) pour éviter les requêtes N+1 entre services.

## 1.4 Données

- Une base PostgreSQL par service (isolation des données, évolution indépendante des schémas).
- Schémas versionnés par **Flyway** (`src/main/resources/db/migration`) ; Hibernate est en mode
  `validate` : il vérifie le schéma mais ne le modifie jamais.
- Dans Kubernetes, PostgreSQL est un `StatefulSet` avec un volume persistant (`local-path`).

## 1.5 Frontend

- Angular 20, composants autonomes, signals, routage avec gardes d'authentification et de rôle.
- Un intercepteur HTTP ajoute le jeton JWT et redirige vers la connexion en cas de 401.
- Compilation AOT, mode strict (`strictTemplates`), budgets de taille, sans source maps en production.
- Servi par `nginx-unprivileged` (utilisateur non root, port 8080) avec en-têtes de sécurité.

## 1.6 Déploiement

| Environnement | Moyen | Point d'entrée |
|---|---|---|
| Poste de développement | Docker Compose | http://localhost:8080 |
| Laboratoire (VMware) | Kubernetes kubeadm, 1 master + 2 workers | http://makzeo.192.168.80.11.nip.io |

Les manifestes Kubernetes sont organisés avec **Kustomize** : `deploy/k8s/base` décrit
l'application, l'overlay `deploy/k8s/overlays/onprem` ajoute l'autoscaling (HPA), les budgets de
disponibilité (PDB), la supervision (ServiceMonitor, PrometheusRule, tableau de bord Grafana), le
registre Nexus et les noms d'hôtes du laboratoire.
