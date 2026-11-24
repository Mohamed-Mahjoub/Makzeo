#!/usr/bin/env python3
"""
Catalogue RÉEL du marché tunisien (informatique, fournitures de bureau, électricité, quincaillerie)
et CAS RÉEL du module IA achats, pour la démonstration devant le jury.

Données réelles (relevées le 24/11/2026) :
  * articles : marques, modèles et références constructeur vendus en Tunisie ;
  * prix publics TTC en dinars publiés par des revendeurs tunisiens (SpaceNet, MyTEK, Tunisianet,
    Tunewtec, NUMEDIA...), le plus souvent via le comparateur primini.tn ; l'URL de chaque relevé est
    conservée dans la fiche article ;
  * fournisseurs : ces revendeurs (nom et site web publics uniquement).

Données simulées (il n'existe pas de source publique) : stocks, consommations, 24 mois d'historique
d'achats de l'entreprise. Les adresses e-mail des fournisseurs du module achats sont des adresses de
démonstration (domaine .local) : les demandes de prix arrivent dans Mailpit, jamais chez le revendeur.

Deux cas réels pour le module IA (prix des offres = prix réellement publiés) :
  1. Toner HP 85A (CE285A) : acheté 389 DT TTC chez MyTEK, publié à 288 DT chez Tunewtec
     -> recommandation « changer de fournisseur » (-26 %) et demande de prix prête à valider.
  2. Rame Navigator A4 80 g : 5 offres réelles, dont 3 articles ÉQUIVALENTS (Archer, Copy Laser,
     Everyday) ; historique en hausse -> prédiction de prix et « achat anticipé ».

Usage (PowerShell : $env:ADMIN_PASSWORD = "...") :
    python backend/scripts/seed_real_catalog.py --gateway http://localhost:4200
    python backend/scripts/seed_real_catalog.py --dry-run          # contrôle sans écrire
"""
import argparse
import importlib.util
import math
import os
import random
import sys
import time
from datetime import date, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent
SURVEY_DATE = "24/11/2026"
VAT = 1.19

# Revendeurs tunisiens (fournisseurs du catalogue) : code, nom, site web public
STORES = {
    "SPACENET": ("SpaceNet", "https://spacenet.tn"),
    "MYTEK": ("MyTEK", "https://www.mytek.tn"),
    "TUNISIANET": ("Tunisianet", "https://www.tunisianet.com.tn"),
    "TUNEWTEC": ("Tunewtec", None),
    "NUMEDIA": ("NUMEDIA", None),
    "CARREFOUR-TN": ("Carrefour Tunisie", None),
    "OXTEK": ("Oxtek", None),
}

P = "https://primini.tn/prix/"
# (catégorie, référence, désignation, marque, prix public TTC relevé, revendeur, URL du relevé, seuil mini)
PRODUCTS = [
    # --- Informatique -------------------------------------------------------------------------
    ("Informatique", "7MD68A", "Imprimante laser monochrome HP LaserJet M111w Wi-Fi", "HP", 409, "SPACENET",
     P + "126315/hp-laserjet-m111w-printer-600-x-600-dpi-a4-wifi", 2),
    ("Informatique", "CE285A", "Toner HP 85A noir original LaserJet (CE285A)", "HP", 389, "MYTEK",
     P + "7382/hp-85a-toner-laserjet-noir-authentique-ce285a", 4),
    ("Informatique", "910-002236", "Souris sans fil Logitech M185 bleu", "Logitech", 32, "SPACENET",
     P + "46877/logitech-m185-souris-ambidextre-rf-sans-fil-optique-1000-dpi", 10),
    ("Informatique", "LOG-MK295", "Pack clavier et souris sans fil Logitech MK295 noir", "Logitech", 119, "MYTEK",
     P + "231404/pack-clavier-et-souris-sans-fil-logitech-mk295-noir", 5),
    ("Informatique", "LOG-PEBBLE2", "Pack clavier et souris sans fil Logitech Pebble 2 Combo noir", "Logitech", 169,
     "TUNISIANET", P + "231401/pack-clavier-et-souris-sans-fil-logitech-pebble-2-combo-noir", 3),
    ("Informatique", "HP-330-COMBO", "Ensemble clavier + souris sans fil HP 330 noir", "HP", 89, "MYTEK",
     P + "212966/ensemble-clavier-souris-hp-sans-fil-330-noir", 5),
    ("Informatique", "E2223HN", "Écran Dell E2223HN 22 pouces", "Dell", 279, "TUNISIANET",
     P + "153158/dell-e-series-ecran-22-e2223hn", 2),
    ("Informatique", "SE2726H", "Écran Dell Pro SE2726H 27 pouces Full HD IPS 144 Hz", "Dell", 428, "TUNISIANET",
     P + "245631/ecran-dell-pro-se2726h-27-full-hd-ips-144hz", 2),
    ("Informatique", "HP-327PF", "Écran HP Série 5 Pro 327pf 27 pouces Full HD 100 Hz IPS", "HP", 509, "MYTEK",
     P + "248620/ecran-hp-serie-5-pro-327pf-27-full-hd-100hz-ips-noir", 1),
    ("Informatique", "KIN-DT70-64", "Clé USB Type-C Kingston DataTraveler 70 64 Go", "Kingston", 32, "SPACENET",
     P + "237672/flash-disk-type-c-kingston-data-traveler-70-64gb-noir", 10),
    ("Informatique", "KIN-DT70-128", "Clé USB Type-C Kingston DataTraveler 70 128 Go", "Kingston", 52, "SPACENET",
     P + "237658/flash-disk-type-c-kingston-data-traveler-70-128gb-noir", 6),
    ("Informatique", "SDK-DUAL-64", "Clé USB SanDisk Ultra Dual Drive Luxe 64 Go Type-C", "SanDisk", 44, "MYTEK",
     P + "214344/cle-usb-sandisk-ultra-dual-drive-luxe-64-go-usb-type-c-gris", 6),
    ("Informatique", "BLK-HDMI-2M", "Câble HDMI Belkin 2 m", "Belkin", 35, "TUNISIANET",
     P + "125341/belkin-hdmi-hdmi-2m-cable-hdmi-hdmi-type-a-standard-noir", 8),
    ("Informatique", "BLK-HDMI-5M", "Câble HDMI Belkin 5 m", "Belkin", 55, "TUNISIANET",
     P + "125342/belkin-hdmi-hdmi-5m-cable-hdmi-hdmi-type-a-standard-noir", 4),
    ("Informatique", "HAMA-HDMI-15", "Câble HDMI HAMA haute vitesse 4K 1,5 m", "Hama", 15.5, "SPACENET",
     P + "221266/cable-hdmi-hama-haute-vitesse-4k-1-5-metres-noir", 8),
    ("Informatique", "LS1016G", "Switch rackable TP-Link LS1016G 16 ports Gigabit", "TP-Link", 245, "SPACENET",
     P + "236961/switch-rackable-tp-link-ls1016g-16-ports-gigabit", 1),
    ("Informatique", "TPL-SW24G", "Switch TP-Link 24 ports Gigabit rackable", "TP-Link", 245, "MYTEK",
     P + "239755/switch-tp-link-24-ports-rackable-giga", 1),
    ("Informatique", "ARCHER-C6", "Routeur Wi-Fi bi-bande TP-Link Archer C6", "TP-Link", 228, "TUNISIANET",
     P + "144969/routeur-wi-fi-bi-bande-2-4ghz-5ghz-archer-c6-blanc-tp-link", 2),
    ("Informatique", "DWR-M920", "Routeur 4G D-Link DWR-M920 300 Mbit/s", "D-Link", 189, "MYTEK",
     P + "214317/routeur-sans-fil-d-link-dwr-m920-300-mbps-4g-noir", 2),
    ("Informatique", "TENDA-A9", "Répéteur Wi-Fi Tenda A9 N300", "Tenda", 89, "SPACENET",
     P + "213316/repeteur-de-portee-tenda-a9-universelle-sans-fil-n300-blanc", 2),
    ("Informatique", "DLK-CAT6A-2M", "Câble réseau patch D-Link RJ45 Cat6A UTP 2 m", "D-Link", 9.9, "MYTEK",
     P + "188738/cable-reseau-patch-d-link-rj45-cat6a-utp-2m-gris", 20),
    ("Informatique", "CAT6-15M", "Câble réseau RJ45 Cat6 UTP 15 m", "Générique", 41, "TUNISIANET",
     P + "180258/cable-rj45-cat6-utp-15m-gris", 5),
    ("Informatique", "CAT6-305M", "Bobine câble réseau Cat6 UTP 305 m", "Générique", 169, "SPACENET",
     P + "178906/bobine-cable-reseau-cat6-305-m-utp-grade-a-bleu", 1),
    # --- Fournitures de bureau ----------------------------------------------------------------
    ("Fournitures de bureau", "NAV-A4-80-500", "Rame papier Navigator A4 80 g/m² extra blanc 500 feuilles",
     "Navigator", 12.5, "SPACENET",
     "https://spacenet.tn/papier-a4/47986-rame-papier-extra-blanc-a4-navigator-80gm-500-feuilles.html", 100),
    ("Fournitures de bureau", "ARCHER-A4-80", "Rame papier Archer A4 80 g 500 feuilles", "Archer", 12.5, "SPACENET",
     "https://spacenet.tn/papier-a4/95092-rame-papier-archer-a4-500-feuilles-80g-blanc.html", 40),
    ("Fournitures de bureau", "NAV-A4-75-500", "Rame papier Navigator A4 75 g/m² 500 feuilles", "Navigator", 11.5,
     "MYTEK", P + "160622/papier-rame-a4-navigator-75g-m2-lot-de-500-feuilles", 40),
    ("Fournitures de bureau", "MYPRINT-A4-70", "Rame papier My Print A4 70 g 500 feuilles", "My Print", 11.9,
     "TUNISIANET", P + "250750/my-print-rame-papier-a4-70-g-500-feuilles-blanc", 40),
    ("Fournitures de bureau", "NAV-OFFICE-250", "Papier Navigator Office A4 250 g/m² 125 feuilles", "Navigator", 13,
     "MYTEK", P + "160828/papier-rame-navigator-office-a4-250g-m2-125-feuilles", 10),
    ("Fournitures de bureau", "GARDE-A4-170", "Rame page de garde A4 couché mat 170 g 100 feuilles", "Générique",
     13.5, "TUNISIANET", P + "178068/rame-page-de-garde-a4-couche-mat-170g-100f", 10),
    ("Fournitures de bureau", "CANON-MATTE-A4", "Papier photo Canon Matte A4", "Canon", 18.9, "SPACENET",
     P + "191803/papier-photo-canon-matte-a4-210x297-mm", 5),
    ("Fournitures de bureau", "BIC-CRISTAL-B50", "Boîte de 50 stylos BIC Cristal Original bleu", "BIC", 24.9,
     "SPACENET", "https://spacenet.tn/stylo/36947-boite-de-50-stylo-bic-cristal-original-bleu.html", 10),
    ("Fournitures de bureau", "BIC-SOFT-R50", "Boîte de 50 stylos BIC Cristal Soft rouge", "BIC", 28, "MYTEK",
     P + "103712/boite-de-50-stylos-bic-cristal-soft-rouge", 5),
    ("Fournitures de bureau", "THERM-80X70", "Rouleau papier thermique 80 x 70 mm", "Générique", 3.5, "TUNISIANET",
     P + "178077/rouleau-de-papier-thermique-80-x-70-mm-a1-55g", 50),
    ("Fournitures de bureau", "DELI-E2003", "Cutter Deli E2003 jaune", "Deli", 4.5, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 10),
    # --- Électricité ----------------------------------------------------------------------------
    ("Électricité", "BV650I", "Onduleur in-line APC Easy UPS BV650I 650 VA AVR", "APC", 299, "SPACENET",
     P + "8170/apc-bv650i-alimentation-d-energie-non-interruptible-interactivite-de-ligne-0-65-kva-375-w-1-sortie-s-ca-bv650i",
     2),
    ("Électricité", "EATON-PS6F", "Multiprise parafoudre Eaton Protection Strip 6 prises (PS6F)", "Eaton", 59,
     "TUNISIANET",
     "https://www.tunisianet.com.tn/multiprise/51148-multiprise-parafoudre-eaton-protection-strip-6-prises-fr.html",
     4),
    ("Électricité", "GAPE-MP3", "Multiprise Gape 3 sorties 2P+T avec interrupteur", "Gape", 11.5, "SPACENET",
     "https://spacenet.tn/533-multiprises", 10),
    ("Électricité", "STIEL-34131", "Multiprise Stiel 34131 3 sorties 2P+T avec interrupteur", "Stiel", 11.9,
     "SPACENET", "https://spacenet.tn/533-multiprises", 10),
    ("Électricité", "SIMEF-MP4", "Multiprise Simef 4 sorties 2P+T avec interrupteur", "Simef", 15, "SPACENET",
     "https://spacenet.tn/533-multiprises", 8),
    ("Électricité", "SIMEF-34331S", "Multiprise Simef 34331S 5 sorties 2P+T avec interrupteur", "Simef", 16.5,
     "SPACENET", "https://spacenet.tn/533-multiprises", 8),
    ("Électricité", "PHILIPS-LED-821688", "Ampoule LED Philips (EAN 8718696821688)", "Philips", 10.9,
     "CARREFOUR-TN", P + "78143/philips-8718696821688-ampoule-led", 20),
    ("Électricité", "DT9205A", "Multimètre numérique DT9205A écran LCD", "Générique", 18.9, "SPACENET",
     "https://spacenet.tn/550-outils-de-bricolage", 3),
    # --- Quincaillerie --------------------------------------------------------------------------
    ("Quincaillerie", "STANLEY-CAD40", "Cadenas à clé laiton Stanley 40 mm", "Stanley", 13.5, "OXTEK",
     P + "65693/cadenas-a-cle-en-laiton-or-stanley-40mm", 6),
    ("Quincaillerie", "EPLRD0711", "Pince coupante Emtop EPLRD0711 180 mm", "Emtop", 14, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 5),
    ("Quincaillerie", "ECLPG0401", "Serre-joint Emtop ECLPG0401 100 mm", "Emtop", 21.5, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 4),
    ("Quincaillerie", "EHKY3092", "Jeu de 9 clés Torx Emtop EHKY3092 T10-T50", "Emtop", 21, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 4),
    ("Quincaillerie", "HOTECHE-CLIQ12", "Clé à cliquet 1/2 pouce 24 dents Hoteche", "Hoteche", 24, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 3),
    ("Quincaillerie", "EPBXK0302-14", "Caisse à outils Emtop EPBXK0302 14 pouces", "Emtop", 29.9, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 3),
    ("Quincaillerie", "ESLL36001", "Niveau à bulle Emtop ESLL36001 60 cm", "Emtop", 48, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 2),
    ("Quincaillerie", "EBCR1801", "Coupe-boulon Emtop EBCR1801 450 mm", "Emtop", 44.5, "SPACENET",
     "https://spacenet.tn/1365-outils-a-main", 2),
    ("Quincaillerie", "BOSCH-GSB400", "Perceuse Bosch GSB 400 400 W", "Bosch", 112, "SPACENET",
     "https://spacenet.tn/550-outils-de-bricolage", 2),
    ("Quincaillerie", "BOSCH-GSB600", "Perceuse à percussion Bosch GSB 600 600 W", "Bosch", 119, "SPACENET",
     "https://spacenet.tn/550-outils-de-bricolage", 2),
    ("Quincaillerie", "BOSCH-PKP18E", "Pistolet à colle Bosch PKP 18 E", "Bosch", 74, "SPACENET",
     "https://spacenet.tn/550-outils-de-bricolage", 2),
    ("Quincaillerie", "BOSCH-GLM25", "Télémètre laser Bosch GLM 25-23", "Bosch", 135, "SPACENET",
     "https://spacenet.tn/550-outils-de-bricolage", 1),
]

# --- Cas réels du module IA : offres réellement publiées (prix TTC) -----------------------------
# (fournisseur, site, e-mail de démonstration, intitulé de l'offre, équivalence, prix TTC, délai j, qualité 1-5)
CASES = {
    "CE285A": {
        "history": {"supplier": "MyTEK", "start": 300.0, "monthly_qty": 3},  # HT simulés, dernier achat = 389 TTC
        "stock": (21, 18),  # entrée, sortie (consommation 3/mois sur 6 mois) -> 3 en stock, seuil 4 : alerte
        "offers": [
            ("Tunewtec", None, "devis.tunewtec@demo.makzeo.local", "Toner LaserJet HP 85A Noir Original (CE285A)",
             "IDENTICAL", 288, 3, 5),
            ("NUMEDIA", None, "devis.numedia@demo.makzeo.local", "Toner Hp 85A LaserJet Noir original", "IDENTICAL",
             320, 3, 5),
            ("SpaceNet", "https://spacenet.tn", "devis.spacenet@demo.makzeo.local", "Toner original HP LaserJet 85A noir",
             "IDENTICAL", 377, 2, 5),
            ("MyTEK", "https://www.mytek.tn", "devis.mytek@demo.makzeo.local", "Toner Original LaserJet HP 85A - Noir",
             "IDENTICAL", 389, 2, 5),
        ],
        "rfq_to": "Tunewtec",  # demande de prix prête à valider : confirmer prix et délai pour 36 toners
    },
    "NAV-A4-80-500": {
        "history": {"supplier": "SpaceNet", "start": 7.1, "monthly_qty": 70, "season": 0.01},  # hausse ~1,7 %/mois
        "stock": (600, 420),  # consommation 70 rames/mois
        "offers": [
            ("SpaceNet", "https://spacenet.tn", "devis.spacenet@demo.makzeo.local",
             "Rame Papier Extra Blanc A4 Navigator 80g/m² 500 Feuilles", "IDENTICAL", 12.5, 2, 5),
            ("Tunisianet", "https://www.tunisianet.com.tn", "devis.tunisianet@demo.makzeo.local",
             "Rame papier Navigator A4 80g/m² Extra Blanc", "IDENTICAL", 12.9, 2, 5),
            ("Tunisianet", "https://www.tunisianet.com.tn", "devis.tunisianet@demo.makzeo.local",
             "Rame papier Copy Laser A4 80g/m² 500 feuilles", "EQUIVALENT", 11.9, 2, 4),
            ("SpaceNet", "https://spacenet.tn", "devis.spacenet@demo.makzeo.local",
             "Rame Papier Archer A4 500 Feuilles 80G Blanc", "EQUIVALENT", 12.5, 2, 4),
            ("FK Informatique", None, "devis.fkinformatique@demo.makzeo.local",
             "Rame papier A4 Everyday 80g/m²", "EQUIVALENT", 13, 4, 4),
        ],
    },
}


def load_helpers():
    spec = importlib.util.spec_from_file_location("seed_large_catalog", HERE / "seed_large_catalog.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def r3(x):
    return round(x + 1e-9, 3)


def items():
    out = []
    for cat, ref, name, brand, ttc, store, url, min_stock in PRODUCTS:
        out.append({"category": cat, "reference": ref, "name": name, "brand": brand, "ttc": ttc,
                    "store": store, "url": url, "minStock": min_stock, "maxStock": min_stock * 5,
                    "purchasePrice": r3(ttc / VAT), "salePrice": r3(ttc)})
    return out


def history_rows(it, rng, months, today):
    """Achats mensuels simulés de l'entreprise, se terminant au prix réel actuel (HT)."""
    case = CASES.get(it["reference"], {}).get("history")
    final = it["purchasePrice"]
    supplier = STORES[it["store"]][0]
    if case:
        start, qty, season = case["start"], case["monthly_qty"], case.get("season", 0.0)
        supplier = case["supplier"]
        rng = random.Random(sum(map(ord, it["reference"])))  # cas de démonstration : série reproductible
    else:
        start = final / ((1 + rng.uniform(0.001, 0.006)) ** (months - 1))  # inflation modérée
        qty, season = max(1, it["minStock"] // 2), 0.0
    growth = (final / start) ** (1 / (months - 1))
    rows = []
    for m in range(months):
        y, mo = today.year, today.month - (months - m)
        while mo <= 0:
            mo += 12
            y -= 1
        price = start * growth ** m
        if m < months - 1:
            # pic de rentrée (septembre) pour le papier, léger bruit sinon
            price *= 1 + season * math.cos(2 * math.pi * (mo - 9) / 12)
            price *= rng.uniform(0.992, 1.008)
        else:
            price = final
        rows.append(f"{y:04d}-{mo:02d}-{rng.randint(3, 25):02d};{it['reference']};{supplier};"
                     f"{max(1, int(qty * rng.uniform(0.8, 1.2)))};{r3(price):.3f}")
    return rows


def main():
    rng = random.Random(ARGS.seed)
    catalog = items()
    print(f"== {len(catalog)} articles réels relevés le {SURVEY_DATE}")
    for cat in sorted({i['category'] for i in catalog}):
        print(f"   {sum(1 for i in catalog if i['category'] == cat):>3}  {cat}")
    if ARGS.dry_run:
        for it in catalog[:6]:
            print(f"   {it['reference']:<16} {it['name'][:55]:<55} {it['ttc']:>8.3f} DT TTC ({STORES[it['store']][0]})")
        for ref in CASES:
            print("   cas IA", ref, history_rows(next(i for i in catalog if i["reference"] == ref), rng, 24,
                                           date.today())[::6])
        return

    S = load_helpers()
    S.ARGS = ARGS
    started = time.time()
    S.login()

    status, cats = S.call("GET", "/api/categories")
    cat_ids = {c["name"]: c["id"] for c in cats or []}
    for cat in {i["category"] for i in catalog}:
        if cat not in cat_ids:
            status, body = S.call("POST", "/api/categories", {"name": cat})
            S.note(status, body, f"catégorie {cat}")
            cat_ids[cat] = body["id"] if status == 201 else None

    print("== Fournisseurs (revendeurs tunisiens)")
    supplier_ids = {}
    for code, (name, site) in STORES.items():
        status, body = S.call("POST", "/api/suppliers", {
            "code": code, "name": name, "country": "TN",
            "notes": f"Revendeur tunisien. Site : {site or 'non renseigné'}. Prix relevés le {SURVEY_DATE}."})
        S.note(status, body, f"fournisseur {code}")
        if status == 201:
            supplier_ids[code] = body["id"]
        else:
            s, page = S.call("GET", f"/api/suppliers?q={code}&size=5")
            for x in (page or {}).get("content", []) if s == 200 else []:
                if x["code"] == code:
                    supplier_ids[code] = x["id"]

    print("== Articles")
    product_ids, fresh = {}, set()
    for it in catalog:
        status, body = S.call("POST", "/api/products", {
            "reference": it["reference"], "name": it["name"], "categoryId": cat_ids.get(it["category"]),
            "description": f"{it['brand']}. Prix public relevé le {SURVEY_DATE} : {it['ttc']:.3f} DT TTC chez "
                           f"{STORES[it['store']][0]}. Source : {it['url']}",
            "unit": "PIECE", "purchasePrice": it["purchasePrice"], "salePrice": it["salePrice"],
            "minStock": it["minStock"], "maxStock": it["maxStock"], "supplierId": supplier_ids.get(it["store"])})
        S.note(status, body, f"article {it['reference']}")
        if status == 201:
            product_ids[it["reference"]] = body["id"]
            fresh.add(it["reference"])
        else:
            s, page = S.call("GET", f"/api/products?q={it['reference']}&size=5")
            for x in (page or {}).get("content", []) if s == 200 else []:
                if x["reference"] == it["reference"]:
                    product_ids[it["reference"]] = x["id"]

    print("== Stocks et consommations simulés")
    for it in catalog:
        if it["reference"] not in fresh:
            continue
        if it["reference"] in CASES:
            initial, out = CASES[it["reference"]]["stock"]
        else:
            plan = S.stock_plan(it, rng)
            if not plan:
                continue
            initial, out = plan
        S.move_stock((product_ids[it["reference"]], supplier_ids.get(it["store"]), initial, out, it["reference"]))

    print("== Historique d'achats simulé (24 mois, se terminant au prix réel actuel)")
    lines = []
    for it in catalog:
        if it["reference"] in fresh:
            lines += history_rows(it, rng, 24, date.today())
    if lines:
        status, body = S.call("POST", "/api/procurement/prices/purchases/import", {"csv": "\n".join(lines)})
        print(f"  {body.get('imported') if status == 200 else status} achats importés"
              + (f", {len(body['rejected'])} rejetés : {body['rejected'][:3]}" if status == 200 and body['rejected']
                 else ""))

    print("== Cas réels du module IA : fournisseurs du marché et offres publiées")
    status, page = S.call("GET", "/api/procurement/suppliers?size=500")
    market = {x["name"].lower(): x for x in (page or {}).get("content", [])} if status == 200 else {}
    valid_until = (date.today() + timedelta(days=30)).isoformat()
    for ref, case in CASES.items():
        pid = product_ids.get(ref)
        if not pid:
            continue
        s, existing = S.call("GET", f"/api/procurement/quotes?productId={pid}")
        if s == 200 and existing:
            print(f"  {ref} : offres déjà enregistrées, ignoré")
            continue
        for name, site, email, title, equivalence, ttc, lead, quality in case["offers"]:
            sup = market.get(name.lower())
            if not sup:
                st, sup = S.call("POST", "/api/procurement/suppliers", {
                    "name": name, "website": site, "email": email,
                    "notes": "Revendeur tunisien. E-mail de démonstration (Mailpit), à remplacer par le contact réel."})
                S.note(st, sup, f"fournisseur marché {name}")
                if st != 201:
                    continue
                market[name.lower()] = sup
            elif not sup.get("email"):
                st, sup = S.call("PUT", f"/api/procurement/suppliers/{sup['id']}", {
                    "name": sup["name"], "website": sup.get("website") or site, "email": email,
                    "phone": sup.get("phone"), "city": sup.get("city"), "notes": sup.get("notes")})
                market[name.lower()] = sup if st == 200 else market[name.lower()]
            if sup.get("status") == "CANDIDATE":
                S.call("PATCH", f"/api/procurement/suppliers/{sup['id']}/validate")
            st, body = S.call("POST", "/api/procurement/quotes", {
                "productId": pid, "marketSupplierId": sup["id"],
                "offeredName": f"{title} (prix publié le {SURVEY_DATE})", "equivalence": equivalence,
                "unitPrice": ttc, "priceIncludesVat": True, "minQuantity": 1, "availability": "IN_STOCK",
                "leadTimeDays": lead, "validUntil": valid_until, "qualityScore": quality})
            S.note(st, body, f"offre {name} {ref}")
        if case.get("rfq_to"):
            sup = market.get(case["rfq_to"].lower())
            if sup:
                st, body = S.call("POST", "/api/procurement/rfqs", {"productId": pid, "marketSupplierId": sup["id"],
                                                                    "quantity": 36})
                S.note(st, body, f"demande de prix {ref}")

    status, body = S.call("POST", "/api/procurement/recommendations/refresh")
    print(f"== Recalcul des prédictions et recommandations : {status} {body if status != 200 else ''}")
    print(f"\nTerminé en {(time.time() - started):.0f} s : {S.stats['created']} créations, "
          f"{S.stats['existing']} déjà présents, {S.stats['errors']} erreurs.")
    print("Scénario de démonstration : docs/10-demo-jury.md")
    if S.stats["errors"]:
        sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Catalogue réel tunisien + cas réels du module IA (démo jury).")
    parser.add_argument("--gateway", default=os.environ.get("MAKZEO_URL", "http://localhost:8080"))
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"))
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--dry-run", action="store_true")
    ARGS = parser.parse_args()
    if not ARGS.dry_run and not ARGS.admin_password:
        parser.error("mot de passe admin manquant : --admin-password ou variable ADMIN_PASSWORD")
    main()
