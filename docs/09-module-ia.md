# 9. Module IA achats

Le module **Achats IA** (`procurement-service`, port 8086, base `procurement_db`) assiste le
responsable des achats sur cinq fonctions :

| # | Fonction | Ce que fait le module |
|---|---|---|
| 1 | Veille mensuelle du marché tunisien | Recherche, pour chaque article du catalogue, les fournisseurs qui proposent le **même article** ou un **équivalent**, et détecte les **nouveaux fournisseurs** |
| 2 | Demandes de prix et comparaison | Rédige les demandes de prix (RFQ), les envoie par e-mail **après validation**, collecte les devis, compare qualité / prix / délai et recommande le meilleur fournisseur |
| 3 | Prédiction des prix | Prévoit l'évolution du prix de chaque article (historique d'achats, saisonnalité, inflation, signal marché) avec **% de variation, horizon et niveau de confiance** |
| 4 | Achats anticipés | Calcule la quantité optimale à acheter maintenant (stock, consommation, budget, coût de stockage), compare coût actuel et coût futur, **alerte** le responsable |
| 5 | Économies et ROI | Mesure les économies réalisées et potentielles par article, par fournisseur et par période |

Principe : **l'IA propose, l'humain décide**. Aucun e-mail n'est envoyé, aucun fournisseur
n'est retenu et aucun achat n'est déclenché sans action d'un utilisateur ADMIN ou
GESTIONNAIRE. L'IA ne produit jamais un prix « inventé » : un prix n'entre dans les calculs que
s'il provient d'un achat réel, d'un devis reçu, d'une page web citée ou du catalogue.

## 9.1 Architecture

```
 Angular /achats ──► Ingress /api/procurement ──► procurement-service (8086)
                                                   │  domaine : OfferComparator, PriceForecaster,
                                                   │            AnticipatedPurchaseOptimizer, SavingsCalculator
                                                   ├──► product / supplier / stock-service (REST + JWT relayé)
                                                   ├──► API Claude (veille web, rédaction RFQ, lecture de devis)
                                                   ├──► ou Serper + Tavily (veille web sans modèle de langage)
                                                   ├──► SMTP (Mailpit en démonstration)
                                                   └──► PostgreSQL procurement_db (Flyway V1)
```

- Les **calculs** (comparaison, prévision, optimisation, économies) sont des algorithmes
  déterministes du domaine, testés unitairement : ils fonctionnent **sans clé d'API**.
- L'**IA** (API Claude, outil `web_search` limité à la Tunisie) sert à trois tâches : trouver
  des fournisseurs et des offres sur le web, rédiger le texte des RFQ, extraire les données
  d'un devis collé en texte. Ses réponses sont contraintes par un schéma JSON (sortie
  structurée), puis validées côté serveur.
- Sans `AI_API_KEY`, la veille web peut passer par **Serper + Tavily** (section 9.5). Sans aucune
  clé, le module reste utilisable : RFQ rédigées par modèle, prédictions statistiques sans signal
  marché. La lecture d'un devis collé fonctionne dans tous les cas : par l'API Claude si elle est
  configurée, sinon par un lecteur de texte (libellés « Prix unitaire », « Délai », « Valable
  jusqu'au »…).

## 9.2 Déroulement

1. **Veille** (le 1er de chaque mois à 6 h 30, heure de Tunis, ou bouton *Lancer la veille*) :
   synchronise les fournisseurs du catalogue, interroge l'IA pour chaque article (25 au
   maximum par exécution), enregistre les fournisseurs découverts au statut **CANDIDATE** et les
   offres trouvées, prépare les RFQ en **brouillon**, met à jour les prédictions et les
   recommandations. Une seule veille peut tourner à la fois (verrou en base, valable aussi avec
   plusieurs réplicas).
2. **Validation** : le responsable valide ou rejette les nouveaux fournisseurs, relit et
   approuve les RFQ (une par une ou en lot). Seules les RFQ approuvées vers un fournisseur
   validé disposant d'un e-mail sont envoyées.
3. **Devis** : saisie manuelle, ou texte du devis collé puis extrait par l'IA (formulaire
   prérempli à vérifier). Les prix TTC sont convertis en HT (TVA 19 %).
4. **Comparaison** : score = 60 % prix effectif (incluant la quantité minimale), 20 % qualité,
   20 % délai ; un article équivalent est légèrement pénalisé ; offres expirées ou indisponibles
   exclues.
5. **Recommandations** : *changer de fournisseur* si l'économie atteint 5 %, *acheter par
   anticipation* si la hausse prévue atteint 3 % avec une confiance au moins moyenne. Les
   nouvelles opportunités sont envoyées par e-mail à `PROCUREMENT_MANAGER_EMAILS`.
6. **Décision** : accepter ou écarter ; les économies acceptées alimentent le ROI.

## 9.3 Algorithmes

| Calcul | Méthode |
|---|---|
| Tendance | Régression log-linéaire sur les prix moyens mensuels ; R² pondéré par la taille de l'historique (pleine confiance à 12 mois) ; repli sur l'inflation annuelle (5,5 % par défaut) si l'historique est court |
| Saisonnalité | Écart moyen du mois cible, utilisé à partir de 24 mois d'historique |
| Signal marché | Variation constatée par la veille web, mélangée à la tendance |
| Confiance | 0,2 + 0,45 × R² × couverture + 0,15 (saisonnalité) + 0,1 (signal) ; plafonnée à 0,3 sous 3 mois. ÉLEVÉE ≥ 0,65, MOYENNE ≥ 0,4 |
| Achat anticipé | Simulation mois par mois : on achète tant que la hausse évitée dépasse le coût de stockage (1,5 %/mois), dans la limite de la consommation sur l'horizon et du budget |
| Économies | (prix de référence − prix retenu) × quantité ; regroupement par article, fournisseur et mois |

## 9.4 Mise en service

### Docker Compose (poste de développement)

Dans `.env` (aucune valeur n'est versionnée) :

```bash
AI_API_KEY=<clé de l'API Claude, facultative>
# ou, sans clé Claude :
SERPER_API_KEY=<clé Serper>
TAVILY_API_KEY=<clé Tavily, facultative>
PROCUREMENT_MANAGER_EMAILS=responsable.achats@makzeo.local
# SMTP_HOST / SMTP_PORT / SMTP_USERNAME / SMTP_PASSWORD : laisser vides pour utiliser Mailpit
```

```bash
docker compose up -d --build
```

- Application : <http://localhost:8080> > menu **Achats IA**.
- Courriels envoyés (RFQ, alertes) : Mailpit sur <http://localhost:8025>.
- Le service `procurement-db-init` crée `procurement_db` si le volume PostgreSQL existait déjà.

### Kubernetes (pipeline Azure DevOps)

Ajouter au groupe de variables `makzeo-secrets` (toutes facultatives, en secret) :

| Variable | Rôle |
|---|---|
| `AI_API_KEY` | Clé de l'API Claude ; prioritaire si elle est renseignée |
| `SERPER_API_KEY` | Clé Serper : veille web sans API Claude |
| `TAVILY_API_KEY` | Clé Tavily : lecture des pages des fournisseurs (prix, e-mail, téléphone) |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Compte d'un SMTP réel (sinon Mailpit, sans authentification) |

Les autres réglages sont dans `deploy/k8s/base/configmap.yaml` (`SMTP_HOST`, `SMTP_PORT`,
`PROCUREMENT_*`, `APP_URL`). Le conteneur d'initialisation du pod crée `procurement_db` sur
une base existante. Mailpit est publié sur <http://mail.192.168.80.11.nip.io>.

> Mailpit capture les messages et ne les remet à personne : il sert à la démonstration. Pour
> écrire réellement aux fournisseurs, renseigner un SMTP réel.

## 9.5 Import de l'historique d'achats

Page **Prix et prévisions > Importer**, fichier CSV UTF-8 séparé par des points-virgules :

```
date;référence;fournisseur;quantité;prix
2025-01-15;PRD-001;Société Alpha;100;12,500
2025-02-12;PRD-001;Société Alpha;80;12,750
```

Prix unitaire HT en dinars (virgule ou point décimal). Les lignes invalides sont signalées sans
bloquer l'import. Plus l'historique est long (idéalement 24 mois), plus la confiance est
élevée.

## 9.6 API

Lecture : ADMIN, GESTIONNAIRE, CONSULTANT. Écriture : ADMIN, GESTIONNAIRE.

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/procurement/overview` | Synthèse (opportunités, économies, dernière veille) |
| GET | `/api/procurement/savings` | Économies et ROI par article, fournisseur, mois |
| POST | `/api/procurement/watch/run` | Lancer la veille (202) |
| GET | `/api/procurement/watch/runs`, `/leads` | Historique des veilles, offres détectées |
| GET / POST / PUT | `/api/procurement/suppliers` | Fournisseurs du marché |
| PATCH | `/api/procurement/suppliers/{id}/validate`, `/reject` | Décision sur un fournisseur candidat |
| GET / POST / PUT | `/api/procurement/rfqs` | Demandes de prix |
| POST | `/api/procurement/rfqs/{id}/approve`, `/rfqs/approve`, `/rfqs/{id}/cancel` | Validation et envoi, en lot, annulation |
| GET / POST | `/api/procurement/quotes` | Devis |
| POST | `/api/procurement/quotes/extract` | Extraction IA d'un devis collé |
| GET | `/api/procurement/quotes/comparison?productId` | Comparaison et meilleur fournisseur |
| GET | `/api/procurement/prices/predictions`, `/prices/{productId}` | Prédictions, historique d'un article |
| POST | `/api/procurement/prices/purchases`, `/prices/purchases/import` | Saisie et import CSV d'achats |
| GET | `/api/procurement/recommendations` | Recommandations |
| POST | `/api/procurement/recommendations/refresh` | Recalcul des recommandations |
| PATCH | `/api/procurement/recommendations/{id}/accept`, `/{id}/dismiss` | Décision |

## 9.7 Supervision

| Métrique | Signification |
|---|---|
| `makzeo_procurement_ai_call_seconds{outcome}` | Durée et nombre d'appels à l'API Claude (`success` / `error`) |
| `makzeo_procurement_watch_runs_total{status}` | Veilles terminées (`completed` / `failed`) |
| `makzeo_procurement_rfq_total{outcome}` | RFQ envoyées (`sent`) ou en échec (`failed`) |
| `makzeo_procurement_opportunities_total{type}` | Opportunités détectées |
| `makzeo_procurement_alerts_total` | E-mails d'alerte envoyés au responsable |
| `makzeo_procurement_open_opportunities` | Recommandations ouvertes (jauge) |
| `makzeo_procurement_potential_savings` | Économies potentielles ouvertes (jauge) |

Alertes : `MakzeoProcurementWatchFailed` (veille en échec sur 24 h) et
`MakzeoProcurementAiErrors` (plus de 5 erreurs IA en une heure). Le tableau de bord Grafana
comporte une rangée **Module IA achats**.

## 9.8 Limites

- La veille dépend de ce qui est publié en ligne : beaucoup de fournisseurs tunisiens
  n'affichent pas leurs prix, d'où les RFQ.
- La qualité (1 à 5) est une note saisie par l'acheteur, pas une mesure automatique.
- Les prévisions sont indicatives ; le niveau de confiance doit être lu avec le chiffre.
- Le coût de l'API Claude croît avec le nombre d'articles surveillés
  (`PROCUREMENT_MAX_PRODUCTS`, `AI_MAX_SEARCHES`).

## 9.5 Veille web avec Serper et Tavily (sans API Claude)

Quand `AI_API_KEY` est vide et `SERPER_API_KEY` renseignée, `MarketIntelligenceRouter` confie la
veille à `WebSearchMarketIntelligence`. Aucune donnée n'est générée par un modèle : tout est lu
dans les résultats de recherche et les pages des fournisseurs, avec des règles fixes et testées
(`WebText`, `QuoteTextParser`).

| Étape | Outil | Ce qui est fait |
|---|---|---|
| 1. Recherche | Serper `/shopping` (Google Shopping, `gl=tn`) | Offres avec marchand et prix affiché |
| 2. Recherche | Serper `/search` (Google, `gl=tn`) | Pages des revendeurs : « désignation référence prix Tunisie » |
| 3. Filtrage | règles | Un fournisseur par domaine ; sites du marché seulement (`.tn`, prix en DT/TND) ; réseaux sociaux et comparateurs exclus |
| 4. Lecture | Tavily `/extract` (5 pages par article, 1 crédit) | Prix de l'article (premier montant en dinars après sa référence, plausible), e-mail, téléphone, ville |
| 5. Classement | règles | **Identique** si la référence constructeur ou l'EAN figure sur la page ; **équivalent** si au moins 50 % des mots-clés de la désignation se retrouvent ; sinon écarté |

- Prix : `289,000 DT`, `1 299,000 TND`, `1.299,000 DT` sont reconnus ; TTC par défaut (prix
  publics), HT si la mention suit le montant. Un prix hors de l'intervalle [÷3 ; ×3] du dernier
  prix payé est ignoré : aucun prix n'est deviné.
- Chaque fournisseur trouvé arrive au statut **Candidat** avec une justification
  (« Référence CE285A trouvée chez Tunewtec. Prix 288.000 TND TTC relevé sur Google Shopping. »).
- Coût par veille (25 articles) : 50 requêtes Serper et environ 25 crédits Tavily, dans les
  offres gratuites des deux services.
- Pas de « signal marché » sans modèle de langage : la prévision des prix reste statistique.
- Les appels sont mesurés dans `makzeo_procurement_ai_call` (opérations `serper_shopping`,
  `serper_search`, `tavily_extract`).

Clés : <https://serper.dev> et <https://tavily.com> (comptes gratuits). Elles se renseignent
comme `AI_API_KEY` : `.env` en local, groupe de variables `makzeo-secrets` dans Azure DevOps.
