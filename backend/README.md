# Makzeo — Backend

Projet Maven multi-modules (Java 21, Spring Boot 4).

| Module | Port | Rôle |
|---|---|---|
| `common-lib` | — | Sécurité JWT, erreurs, clients REST, OpenAPI, supervision |
| `auth-service` | 8081 | Utilisateurs, rôles, jetons |
| `product-service` | 8082 | Produits, catégories, codes-barres |
| `supplier-service` | 8083 | Fournisseurs |
| `stock-service` | 8084 | Mouvements, niveaux, alertes, inventaires |
| `reporting-service` | 8085 | Tableaux de bord et rapports |

```bash
mvn verify                 # compilation et tests
mvn verify -Pcoverage      # + rapports JaCoCo (target/site/jacoco)
```

Images : `Dockerfile` (construction complète, utilisé par Docker Compose) et
`Dockerfile.runtime` (image d'exécution à partir d'un JAR déjà testé, utilisé par le pipeline).

Scripts : `scripts/e2e_test.py` (test de bout en bout), `scripts/seed_large_catalog.py` (catalogue de 10 000 articles), `scripts/seed_demo_data.py` (données de
démonstration). Requêtes d'exemple : `docs/api-requests.http`, `docs/makzeo.postman_collection.json`.

Documentation : [../docs](../docs).
