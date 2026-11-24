# 3. Sécurité

## 3.1 Authentification et autorisation

- **JWT signé HMAC-SHA256** émis par `auth-service`, durée de validité configurable
  (`JWT_VALIDITY`, 8 h par défaut). Chaque service valide lui-même le jeton (architecture sans
  état, pas de session serveur).
- **Clé de signature** `JWT_SECRET` : obligatoire, 32 caractères minimum, identique pour les
  6 services. Le démarrage échoue si elle est absente ou trop courte.
- **Mots de passe** hachés avec **BCrypt** ; jamais renvoyés par l'API.
- **Contrôle d'accès par rôle** (`@PreAuthorize`) sur chaque route ; un compte désactivé ne peut
  plus se connecter.
- **Compte administrateur initial** créé uniquement si `ADMIN_PASSWORD` est fourni et que la base
  est vide ; aucun mot de passe par défaut.

## 3.2 Gestion des secrets

Aucun secret n'est présent dans le code ni dans le dépôt Git.

| Secret | Poste de développement | Kubernetes | Pipeline |
|---|---|---|---|
| POSTGRES_PASSWORD, JWT_SECRET, ADMIN_PASSWORD | fichier `.env` (ignoré par Git) | Secret `stock-secrets` | Groupe de variables `makzeo-secrets` (valeurs masquées) |
| Accès Nexus du pipeline | — | Secret `nexus-registry` (pull des images) | `NEXUS_CI_PASSWORD` |
| Jeton SonarQube | — | — | `SONAR_TOKEN` |
| Accès au cluster | — | Compte de service `ci-deployer` | Fichier sécurisé `makzeo-kubeconfig` |
| Administrateur Grafana | — | Secret `grafana-admin` | — |

Les secrets Kubernetes sont créés par le pipeline à partir du groupe de variables
(`kubectl create secret ... --dry-run=client | kubectl apply`) ; les valeurs sont passées par
variables d'environnement et n'apparaissent jamais dans les journaux.

## 3.3 Protection de l'exposition

- **Limitation des tentatives de connexion** : 10 requêtes par minute et par adresse IP sur
  `POST /api/auth/login` (Ingress NGINX `limit-rpm`, passerelle Nginx `limit_req`) ; au-delà,
  réponse 429.
- **En-têtes de sécurité HTTP** servis par le frontend : `Content-Security-Policy` stricte
  (`script-src 'self'`, `frame-ancestors 'none'`, `object-src 'none'`), `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`.
- **CORS** limité aux origines déclarées (`CORS_ALLOWED_ORIGINS`).
- **Actuator** : seules `health`, `info` et `prometheus` sont exposées, sans détail de santé ;
  `/actuator` n'est pas routé vers l'extérieur.
- **Swagger UI** désactivé en environnement Kubernetes.
- `server_tokens off` : la version de Nginx n'est pas divulguée.

## 3.4 Durcissement des conteneurs et du cluster

| Mesure | Mise en œuvre |
|---|---|
| Utilisateur non root | UID 1001 (backend), `nginx-unprivileged` (frontend), `runAsNonRoot: true` |
| Système de fichiers en lecture seule | `readOnlyRootFilesystem: true`, `/tmp` en `emptyDir` |
| Privilèges minimaux | `allowPrivilegeEscalation: false`, `capabilities: drop [ALL]`, `seccompProfile: RuntimeDefault` |
| Ressources bornées | `requests` / `limits` CPU et mémoire sur chaque conteneur |
| Isolation réseau | `NetworkPolicy` : seuls les pods backend joignent PostgreSQL |
| Moindre privilège du pipeline | Compte `ci-deployer` limité au namespace `stock-management` (+ lecture Prometheus) |
| Images | Construites à partir des artefacts testés, analysées par Trivy (CRITICAL bloquant) |

## 3.5 Sécurité de la chaîne d'intégration

- **SonarQube** : Quality Gate « Makzeo » bloquante (fiabilité, sécurité et maintenabilité notées
  A sur le nouveau code, couverture ≥ 60 %, duplication ≤ 5 %).
- **Trivy** : analyse des dépendances npm (HIGH/CRITICAL) et des 7 images (CRITICAL).
- **Nexus** : accès anonyme désactivé ; le pipeline utilise un compte technique aux droits limités.
- **Branche principale protégée** (recommandé) : pull request obligatoire avec build de validation.
- **Approbation manuelle** avant tout déploiement (environnement `makzeo-onprem`).
