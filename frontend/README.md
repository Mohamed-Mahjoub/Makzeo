# Frontend Angular — Makzeo

Interface web de l'application de gestion de produits et de stock.

| Élément | Choix |
|---|---|
| Framework | Angular 20 (composants *standalone*, **signals**, détection de changements *zoneless*, nouveau *control flow* `@if` / `@for`) |
| Graphiques | Composants SVG développés dans le projet (`shared/charts`) : aucune bibliothèque externe |
| Code-barres | Rendu EAN-13 en SVG (`shared/barcode.ts`) et saisie par douchette (`shared/product-picker.ts`) |
| Sécurité | Jeton JWT (intercepteur HTTP), gardes de routes par rôle |
| Dépendances | Uniquement les paquets Angular officiels + RxJS |

## Démarrage (développement)

Pré-requis : Node.js 20.19+ ou 22, npm, et les 5 microservices lancés (ports 8081-8085).

```bash
cd frontend
npm install
npm start          # = ng serve avec proxy.conf.json
```

Ouvrir http://localhost:4200 et se connecter avec `admin` / `Admin@12345`.

Le fichier `proxy.conf.json` redirige chaque préfixe `/api/...` vers le bon microservice
(`/api/auth` → 8081, `/api/products` → 8082, etc.) : pas de problème CORS en développement.

Build de production : `npm run build`, qui produit `dist/stock-management-frontend/browser`.

## Pages

| Route | Page | Rôles |
|---|---|---|
| `/login` | Connexion | public |
| `/dashboard` | **Tableau de bord décisionnel en 3 vues** : état des stocks, réapprovisionnement, valorisation | tous |
| `/products` | Produits : recherche, création/modification, activation, étiquette code-barres | lecture : tous · écriture : Admin, Gestionnaire |
| `/categories` | Catégories | idem |
| `/suppliers` | Fournisseurs de tous pays (liste ISO des pays, identifiant fiscal adapté au pays, filtre par pays) | idem |
| `/movements` | Entrées / sorties (scan code-barres) + historique filtrable | saisie : Admin, Gestionnaire, Magasinier |
| `/stock` | Niveaux de stock par état | tous |
| `/alerts` | Alertes de rupture / seuil, prise en compte, recalcul | tous |
| `/inventories`, `/inventories/:id` | Inventaires : comptage, écarts, validation, PV PDF/Excel | comptage : Magasinier+ · validation : Admin, Gestionnaire |
| `/reports` | Rapports PDF / Excel | Admin, Gestionnaire, Consultant |
| `/users` | Utilisateurs et rôles | Admin |
| `/settings` | Paramètres : **devise de l'application** (toutes les devises ISO 4217) | lecture : tous · modification : Admin |
| `/profile` | Profil et mot de passe | tous |

## Le tableau de bord

Il reprend les 3 tableaux de bord BI « achats / stocks » (état des stocks, prévision de
réapprovisionnement, valorisation). Les indicateurs sont calculés par `reporting-service`
(`GET /api/reports/analytics?days=30&categoryId=`) :

1. **État des stocks** : KPI (produits, ruptures, sous le seuil, alertes, entrées/sorties),
   évolution quotidienne des entrées vs sorties avec moyenne mobile sur 7 jours, répartition par état,
   top des produits consommés, alertes actives.
2. **Réapprovisionnement** : consommation moyenne par jour, **couverture en jours**, **date de
   rupture estimée**, priorité (critique / haute / moyenne), quantité et montant suggérés,
   raccourci « Entrée » pré-rempli.
3. **Valorisation** : valeur du stock au prix d'achat, taux de rotation, couverture globale,
   **impact des ruptures** (ventes perdues estimées par jour), valeur par catégorie, top 10 des produits par valeur.

Filtres : période (7 / 30 / 90 / 180 jours) et catégorie.

## Devise

Tous les montants passent par `shared/format.ts` (`money()`, `amount()`), qui lit la devise dans un
**signal** Angular. Quand l'administrateur change la devise (page Paramètres), tous les écrans
(prix, tableau de bord, rapports) s'actualisent sans rechargement, avec le bon nombre de décimales
(3 pour le dinar tunisien, 0 pour le franc CFA...). La devise courante est rappelée dans la barre supérieure.

## Structure

```
src/app
├── core/          modèles, services HTTP (un par microservice), intercepteur JWT, gardes
├── shared/        composants réutilisables : icônes, KPI, graphiques SVG, code-barres, modale…
├── layout/        coque de l'application (menu latéral selon le rôle, barre supérieure)
└── features/      une page par fonctionnalité (dashboard, products, movements, inventories…)
```

## Image Docker

`Dockerfile` en 2 étapes : build Node 22, puis serveur `nginx-unprivileged` (port 8080,
fallback SPA dans `nginx.conf`). En Kubernetes, l'Ingress route `/api/**` vers les microservices
et `/` vers ce conteneur.
