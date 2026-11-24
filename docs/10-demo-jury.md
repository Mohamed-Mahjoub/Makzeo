# 10. Démonstration au jury : données réelles et cas réels du module IA

## 10.1 Préparer les données (une fois, la veille de la soutenance)

Repartir d'une base vide pour une démonstration propre :

```powershell
docker compose down -v
docker compose up -d --build
# attendre que les services soient "healthy" : docker compose ps
$env:ADMIN_PASSWORD = "<mot de passe admin>"
python backend\scripts\seed_real_catalog.py --gateway http://localhost:8080
```

| Donnée | Nature | Source |
|---|---|---|
| 55 articles (informatique, fournitures de bureau, électricité, quincaillerie) | **Réels** : marques, modèles, références constructeur | Revendeurs tunisiens |
| Prix publics TTC | **Réels**, relevés le 24/11/2026 (URL dans chaque fiche) | primini.tn, SpaceNet, MyTEK, Tunisianet |
| Fournisseurs du catalogue | **Réels** (nom, site web) | SpaceNet, MyTEK, Tunisianet, Tunewtec, NUMEDIA, Carrefour, Oxtek |
| Offres des deux cas IA | **Réelles** (prix publiés par chaque revendeur) | primini.tn et sites des revendeurs |
| Stocks, consommations, 24 mois d'achats de l'entreprise | Simulés | aucune source publique |
| E-mails des fournisseurs du module achats | Démonstration (`@demo.makzeo.local`) | les demandes de prix arrivent dans Mailpit |

> Réponse type si le jury demande d'où viennent les données : « les articles et les prix sont
> relevés sur le marché tunisien, l'historique d'achats est simulé car c'est une donnée interne
> d'entreprise ; en production il viendrait de l'import CSV des achats réels ».

## 10.2 Scénario (8 à 10 minutes)

### 1. Le constat : une rupture qui menace (Tableau de bord)

- Le **Toner HP 85A (CE285A)** est sous son seuil : 3 en stock pour un seuil de 4, consommation de
  3 par mois.
- Ouvrir **Stocks > Alertes** : l'alerte est générée par l'application.

### 2. Le marché propose mieux : changer de fournisseur (Achats IA > Comparaison des offres)

Choisir l'article **CE285A**. Offres réelles publiées le 24/11/2026 :

| Revendeur | Prix TTC | Prix HT | Écart vs dernier achat |
|---|---|---|---|
| Tunewtec | 288 DT | 242,017 DT | **-26 %** |
| NUMEDIA | 320 DT | 268,908 DT | -18 % |
| SpaceNet | 377 DT | 316,807 DT | -3 % |
| MyTEK (fournisseur actuel) | 389 DT | 326,891 DT | 0 % |

- Le module classe les offres : **60 % prix, 20 % délai, 20 % qualité**, après conversion TTC → HT.
- **Achats IA > Recommandations** : « Tunewtec propose le toner à 242,017 DT HT contre 326,891 DT
  payés, soit **3 055 DT d'économie sur un an** (36 toners) ».
- Message clé : *l'IA propose, le responsable décide*. Bouton **Accepter** : l'économie entre dans
  le ROI.

### 3. Confirmer par une demande de prix (Achats IA > Demandes de prix)

- Une demande de prix au statut **Brouillon** est prête pour Tunewtec (36 toners).
- Ouvrir la demande pour montrer le texte rédigé, puis **Valider et envoyer**.
- Ouvrir **Mailpit** (<http://localhost:8025>) : le courriel est arrivé (aucun envoi sans validation
  humaine).

### 4. Anticiper une hausse : la prédiction de prix (Achats IA > Prix et prévisions)

Choisir le **SSD Kingston A400 480 Go** (369 DT TTC chez MyTEK) :

- 24 mois d'historique d'achats (simulé), en forte hausse, comme le marché de la mémoire flash.
- Prédiction à 3 mois : **environ +10 %**, confiance **élevée**.
- Recommandation **Achat anticipé** : environ 50 unités à acheter maintenant (10 en stock,
  consommation de 10 par mois), coût maintenant vs plus tard, coût de stockage (1,5 % par mois),
  gain net et date « acheter avant ».

Puis la **Rame Navigator A4 80 g** : hausse prédite d'environ +4 % seulement. Le module **ne
recommande pas** de stocker : la hausse ne couvre pas le coût de stockage (1,5 % par mois pendant
3 mois). Bon argument devant le jury : l'outil ne pousse pas à acheter, il calcule si stocker est
rentable.

### 5. Les articles équivalents (Comparaison des offres, même article)

Pour la rame Navigator, la comparaison mélange :

- deux offres **identiques** : SpaceNet 12,5 DT, Tunisianet 12,9 DT ;
- trois **équivalentes** : Copy Laser 11,9 DT, Archer 12,5 DT, Everyday 13 DT.

L'équivalent le moins cher n'est pas automatiquement retenu : un article équivalent perd 5 % de
score et sa note qualité est plus faible. Le module recommande Navigator chez SpaceNet.

### 6. Le ROI (Achats IA > tableau de bord)

- Économies potentielles et réalisées par article, fournisseur et mois.
- Après l'acceptation de l'étape 2, l'économie apparaît dans le ROI.

### 7. (Option, avec une clé `AI_API_KEY` et Internet) Veille en direct

**Lancer la veille** : l'IA (API Claude avec recherche web limitée à la Tunisie) cherche, pour
chaque article, les fournisseurs tunisiens et les équivalents, et prépare les demandes de prix. Les
nouveaux fournisseurs arrivent au statut **Candidat** (Achats IA > Fournisseurs du marché) et
doivent être validés.

## 10.3 Questions probables

| Question | Réponse |
|---|---|
| L'IA invente-t-elle des prix ? | Non : un prix n'entre dans les calculs que s'il vient d'un achat, d'un devis ou d'une page web citée. Les calculs (classement, prévision, quantité optimale) sont des algorithmes déterministes et testés. |
| Pourquoi pas un modèle de deep learning pour les prix ? | Historiques courts (24 points) : une régression log-linéaire avec saisonnalité est plus robuste et explicable ; le niveau de confiance l'indique. |
| Que se passe-t-il sans clé d'API ? | Tout fonctionne sauf la veille web et la lecture automatique des devis. |
| Et si un fournisseur répond par e-mail ? | Coller le devis puis « Lire avec l’IA » (page Comparaison des offres) : extraction automatique, puis vérification humaine. |
