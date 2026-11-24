# Makzeo — Frontend

Application Angular 20 (composants autonomes, signals, routage protégé par rôle).

```bash
npm ci
npm start                  # http://localhost:4200, proxy vers les services (proxy.conf.json)
npm run build:prod         # dist/stock-management-frontend/browser
```

Production : Nginx non privilégié (`nginx.conf`) avec en-têtes de sécurité
(`security-headers.conf`). `Dockerfile` construit l'application ; `Dockerfile.runtime` (pipeline)
emballe une compilation déjà validée.

Documentation : [../docs](../docs).
