#!/usr/bin/env python3
"""
Catalogue réel du marché tunisien pour la démonstration Makzeo.

  * Articles RÉELS : produits vendus en Tunisie, issus d'Open Food Facts (base ouverte, licence ODbL),
    avec leur vrai nom, leur marque, leur contenance et leur vrai code-barres EAN-13
    (priorité aux codes GS1 Tunisie « 619 »).
  * Fournisseurs : les marques / propriétaires de marque déclarés dans Open Food Facts
    (nom uniquement : aucun contact ni identifiant fiscal n'est inventé).
  * Prix : prix INDICATIFS estimés par catégorie et par contenance (TND), à remplacer par vos relevés
    de prix grâce à --prices (fichier généré par --export-template).
  * Stocks et historique d'achats sur 24 mois : SIMULÉS (aucune source publique ne les fournit),
    pour alimenter le tableau de bord et le module IA achats.

Exemples (PowerShell : définir d'abord $env:ADMIN_PASSWORD) :
    python backend/scripts/seed_real_market.py --gateway http://localhost:4200
    python backend/scripts/seed_real_market.py --count 300 --export-template prix_a_relever.csv --dry-run
    python backend/scripts/seed_real_market.py --prices prix_a_relever.csv          # avec vos prix relevés

Le téléchargement Open Food Facts est mis en cache (off_tunisie.json) : les exécutions suivantes
fonctionnent hors ligne et donnent le même catalogue.
"""
import argparse
import csv
import importlib.util
import json
import os
import random
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path

HERE = Path(__file__).resolve().parent
OFF_URL = "https://world.openfoodfacts.org/api/v2/search"
USER_AGENT = "Makzeo-PFE-demo/1.0 (projet de fin d'études, import ponctuel)"
FIELDS = "code,product_name,product_name_fr,generic_name_fr,brands,brand_owner,quantity,categories_tags,countries_tags"

# Règles de classement (ordre significatif) :
#   (mots-clés des catégories Open Food Facts, catégorie Makzeo, prix public TND par kg ou litre,
#    prix public TND par défaut si la contenance est inconnue)
RULES = [
    (("waters",), "Eaux", 0.55, 0.8),
    (("sodas", "carbonated-drinks", "colas"), "Boissons gazeuses", 2.2, 1.8),
    (("juices", "nectars", "fruit-based-beverages", "syrups"), "Jus et sirops", 4.0, 2.5),
    (("coffees", "coffee"), "Café et thé", 38.0, 6.0),
    (("teas", "herbal-teas", "infusions"), "Café et thé", 30.0, 3.0),
    (("olive-oils",), "Huiles", 17.0, 17.0),
    (("vegetable-oils", "oils"), "Huiles", 6.5, 6.5),
    (("milks", "lait"), "Produits laitiers", 1.6, 1.6),
    (("yogurts", "fermented-milk", "desserts-lactes", "dairy-desserts"), "Produits laitiers", 6.0, 0.6),
    (("cheeses",), "Produits laitiers", 30.0, 4.0),
    (("butters", "creams"), "Produits laitiers", 36.0, 4.0),
    (("dairies",), "Produits laitiers", 8.0, 2.0),
    (("couscous",), "Pâtes, couscous et farines", 2.2, 2.0),
    (("pastas", "pasta"), "Pâtes, couscous et farines", 2.4, 1.2),
    (("flours", "semolinas", "semolina"), "Pâtes, couscous et farines", 1.6, 1.6),
    (("breakfast-cereals",), "Céréales et petit-déjeuner", 22.0, 8.0),
    (("chocolates", "cocoa"), "Chocolat et confiserie", 45.0, 2.5),
    (("candies", "confectioneries", "chewing-gum"), "Chocolat et confiserie", 30.0, 1.5),
    (("biscuits", "cakes", "wafers", "cookies", "pastries"), "Biscuits et gâteaux", 14.0, 1.2),
    (("jams", "spreads", "honeys", "halva", "halwa"), "Épicerie sucrée", 18.0, 5.0),
    (("sugars",), "Épicerie sucrée", 1.6, 1.6),
    (("tunas", "sardines", "canned-fishes", "fishes"), "Conserves", 32.0, 4.0),
    (("tomato", "canned", "legumes", "pulses"), "Conserves", 8.0, 2.5),
    (("harissa", "sauces", "condiments", "mayonnaises", "ketchup", "spices", "salts"), "Sauces et condiments", 12.0, 2.5),
    (("dates", "dried-fruits", "nuts", "seeds"), "Fruits secs et dattes", 18.0, 6.0),
    (("snacks", "chips", "crisps", "salty"), "Snacks salés", 25.0, 1.2),
    (("meats", "sausages", "charcuteries", "poultry"), "Charcuterie", 30.0, 6.0),
]
DEFAULT_RULE = ((), "Autres produits alimentaires", 10.0, 3.0)

PRODUCT_ATTRIBUTION = "Source : Open Food Facts (ODbL). Prix indicatif estimé."


def load_seed_module():
    spec = importlib.util.spec_from_file_location("seed_large_catalog", HERE / "seed_large_catalog.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# --------------------------------------------------------------------------------------------
# Open Food Facts
# --------------------------------------------------------------------------------------------
def fetch_off(target, cache):
    if cache.exists():
        products = json.loads(cache.read_text(encoding="utf-8"))
        print(f"== Open Food Facts : {len(products)} produits lus depuis le cache {cache}")
        return products
    print("== Téléchargement des produits vendus en Tunisie depuis Open Food Facts")
    products, page, page_size = [], 1, 100
    wanted = max(target * 3, 300)  # une partie est écartée (nom, code-barres)
    while len(products) < wanted:
        params = {"countries_tags_en": "tunisia", "fields": FIELDS, "page": page, "page_size": page_size,
                  "sort_by": "unique_scans_n"}
        req = urllib.request.Request(OFF_URL + "?" + urllib.parse.urlencode(params),
                                     headers={"User-Agent": USER_AGENT})
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    body = json.loads(resp.read())
                break
            except Exception as e:  # réseau, 429, 5xx
                if attempt == 2:
                    if products:
                        print(f"\n  arrêt du téléchargement ({e}) : {len(products)} produits conservés")
                        body = {"products": []}
                        break
                    sys.exit(f"Open Food Facts injoignable : {e}")
                time.sleep(15)
        batch = body.get("products", [])
        products += batch
        print(f"\r  page {page} : {len(products)} produits (sur {body.get('count', '?')})", end="", flush=True)
        if len(batch) < page_size:
            break
        page += 1
        time.sleep(7)  # limite publique de l'API de recherche : 10 requêtes par minute
    print()
    cache.write_text(json.dumps(products, ensure_ascii=False), encoding="utf-8")
    print(f"  cache enregistré : {cache}")
    return products


def ean13_ok(code):
    if not re.fullmatch(r"\d{13}", code or ""):
        return False
    total = sum(int(d) * (1 if i % 2 == 0 else 3) for i, d in enumerate(code[:12]))
    return (10 - total % 10) % 10 == int(code[12])


def latin(text):
    """Vrai si le texte est majoritairement en alphabet latin (affichage homogène dans l'interface)."""
    letters = [c for c in text if c.isalpha()]
    return bool(letters) and sum(1 for c in letters if ord(c) < 0x250) / len(letters) > 0.8


def clean(text):
    return re.sub(r"\s+", " ", (text or "").replace(" ", " ")).strip(" ,;-")


def parse_quantity(qty):
    """'6 x 1,5 L' -> (9.0, 'L') ; '500 g' -> (0.5, 'KG') ; None si inconnue."""
    q = (qty or "").lower().replace(",", ".")
    m = re.search(r"(?:(\d+)\s*[x×]\s*)?(\d+(?:\.\d+)?)\s*(kg|g|gr|l|cl|ml)\b", q)
    if not m:
        return None
    count = int(m.group(1) or 1)
    value = float(m.group(2)) * count
    unit = m.group(3)
    factor = {"kg": 1, "g": 0.001, "gr": 0.001, "l": 1, "cl": 0.01, "ml": 0.001}[unit]
    total = value * factor
    return (total, "L" if unit in ("l", "cl", "ml") else "KG") if 0.005 <= total <= 50 else None


def classify(tags):
    joined = " ".join(tags or [])
    for rule in RULES:
        if any(k in joined for k in rule[0]):
            return rule
    return DEFAULT_RULE


def slug(text, size):
    ascii_text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().upper()
    return re.sub(r"[^A-Z0-9]+", "-", ascii_text).strip("-")[:size] or "MARQUE"


def build_catalog(raw, count, rng):
    seen, items = set(), []
    for p in raw:
        code = (p.get("code") or "").strip()
        if not ean13_ok(code) or code in seen:
            continue
        name = clean(p.get("product_name_fr") or p.get("product_name") or p.get("generic_name_fr"))
        brand = clean((p.get("brands") or "").split(",")[0])
        if len(name) < 3 or not brand or not latin(name):
            continue
        seen.add(code)
        _, category, per_unit, per_piece = classify(p.get("categories_tags"))
        quantity = clean(p.get("quantity"))
        parsed = parse_quantity(quantity)
        retail = per_unit * parsed[0] if parsed else per_piece
        retail = round(min(max(retail * rng.uniform(0.9, 1.12), 0.3), 150), 3)
        full_name = name if brand.lower() in name.lower() else f"{name} {brand}"
        if quantity and quantity.lower() not in full_name.lower():
            full_name = f"{full_name} {quantity}"
        cheap = retail < 5
        min_stock = rng.choice([24, 36, 48, 60, 96]) if cheap else rng.choice([6, 10, 12, 20])
        items.append({
            "code": code,
            "reference": f"TN-{code}",
            "name": full_name[:150],
            "brand": brand,
            "owner": clean(p.get("brand_owner")) or brand,
            "category": category,
            "quantity": quantity,
            "salePrice": retail,
            "purchasePrice": round(retail * rng.uniform(0.72, 0.85), 3),
            "minStock": min_stock,
            "maxStock": min_stock * rng.choice([4, 5, 6]),
            "made_in_tn": code.startswith("619"),
        })
    # priorité aux produits fabriqués en Tunisie (préfixe GS1 619), puis popularité Open Food Facts
    items.sort(key=lambda it: not it["made_in_tn"])
    return items[:count]


def apply_prices(items, path):
    """Remplace les prix estimés par les prix relevés (colonnes code;prix_vente[;prix_achat])."""
    with open(path, encoding="utf-8-sig", newline="") as fh:
        rows = list(csv.DictReader(fh, delimiter=";"))
    prices = {}
    for r in rows:
        try:
            sale = float((r.get("prix_vente") or "").replace(",", "."))
        except ValueError:
            continue
        buy = (r.get("prix_achat") or "").replace(",", ".")
        prices[(r.get("code") or "").strip()] = (sale, float(buy) if buy else round(sale * 0.8, 3))
    updated = 0
    for it in items:
        if it["code"] in prices:
            it["salePrice"], it["purchasePrice"] = prices[it["code"]]
            updated += 1
    print(f"== Prix relevés appliqués : {updated} articles (fichier {path})")


def export_template(items, path):
    with open(path, "w", encoding="utf-8-sig", newline="") as fh:
        w = csv.writer(fh, delimiter=";")
        w.writerow(["code", "article", "marque", "categorie", "contenance", "prix_vente", "prix_achat"])
        for it in items:
            w.writerow([it["code"], it["name"], it["brand"], it["category"], it["quantity"],
                        f"{it['salePrice']:.3f}", f"{it['purchasePrice']:.3f}"])
    print(f"== Modèle de relevé de prix exporté : {path} ({len(items)} articles)")
    print("   Corrigez les colonnes prix_vente / prix_achat (TND) puis relancez avec --prices")


# --------------------------------------------------------------------------------------------
# Chargement dans Makzeo
# --------------------------------------------------------------------------------------------
def main():
    rng = random.Random(ARGS.seed)
    raw = fetch_off(ARGS.count, Path(ARGS.cache))
    items = build_catalog(raw, ARGS.count, rng)
    if not items:
        sys.exit("Aucun produit exploitable (code EAN-13 valide, nom et marque)")
    if ARGS.prices:
        apply_prices(items, ARGS.prices)
    if ARGS.export_template:
        export_template(items, ARGS.export_template)

    by_cat = {}
    for it in items:
        by_cat[it["category"]] = by_cat.get(it["category"], 0) + 1
    owners = sorted({it["owner"] for it in items})
    print(f"== Catalogue : {len(items)} articles réels ({sum(it['made_in_tn'] for it in items)} fabriqués en "
          f"Tunisie), {len(by_cat)} catégories, {len(owners)} marques / fournisseurs")
    for cat, n in sorted(by_cat.items(), key=lambda kv: -kv[1]):
        print(f"   {n:>5}  {cat}")
    if ARGS.dry_run:
        for it in items[:10]:
            print(f"   {it['code']}  {it['name'][:60]:<60} {it['salePrice']:>8.3f} TND")
        return

    S = load_seed_module()
    S.ARGS = ARGS
    started = time.time()
    print(f"== Connexion à {ARGS.gateway}")
    S.login()

    # Catégories
    status, existing = S.call("GET", "/api/categories")
    if status != 200:
        sys.exit(f"Lecture des catégories impossible ({status}) : {existing}")
    cat_ids = {c["name"].lower(): c["id"] for c in existing}
    for cat in by_cat:
        if cat.lower() not in cat_ids:
            status, body = S.call("POST", "/api/categories",
                                  {"name": cat, "description": "Produits du marché tunisien (Open Food Facts)"})
            S.note(status, body, f"catégorie {cat}")
            if status == 201:
                cat_ids[cat.lower()] = body["id"]

    # Fournisseurs = marques / propriétaires de marque (aucune coordonnée inventée)
    print(f"== Fournisseurs ({len(owners)})")
    supplier_ids = {}
    for owner in owners:
        code = "TN-" + slug(owner, 27)
        status, body = S.call("POST", "/api/suppliers", {
            "code": code, "name": owner[:150], "country": "TN",
            "notes": "Marque ou propriétaire de marque déclaré dans Open Food Facts. Coordonnées à compléter."})
        S.note(status, body, f"fournisseur {code}")
        if status == 201:
            supplier_ids[owner] = body["id"]
        else:
            s, page = S.call("GET", f"/api/suppliers?q={urllib.parse.quote(code)}&size=5")
            for x in (page or {}).get("content", []) if s == 200 else []:
                if x["code"] == code:
                    supplier_ids[owner] = x["id"]

    # Articles
    def create(it):
        payload = {"reference": it["reference"], "name": it["name"],
                   "description": f"{it['brand']} – {it['quantity'] or 'contenance non précisée'}. {PRODUCT_ATTRIBUTION}",
                   "categoryId": cat_ids.get(it["category"].lower()), "barcode": it["code"], "unit": "PIECE",
                   "purchasePrice": it["purchasePrice"], "salePrice": it["salePrice"],
                   "minStock": it["minStock"], "maxStock": it["maxStock"], "supplierId": supplier_ids.get(it["owner"])}
        status, body = S.call("POST", "/api/products", payload)
        S.note(status, body, f"article {it['code']}")
        if status == 201:
            return it["reference"], body["id"], True
        if status == 409:
            s, page = S.call("GET", f"/api/products?q={it['reference']}&size=5")
            for x in (page or {}).get("content", []) if s == 200 else []:
                if x["reference"] == it["reference"]:
                    return it["reference"], x["id"], False
        return it["reference"], None, False

    print(f"== Articles ({len(items)})")
    results = S.parallel(items, create, "articles")
    ids = {ref: pid for ref, pid, _ in results if pid}
    fresh = {ref for ref, pid, new in results if pid and new}

    # Stocks simulés
    if not ARGS.skip_stock:
        moves = []
        for it in items:
            plan = S.stock_plan(it, rng)
            if plan and it["reference"] in fresh:
                moves.append((ids[it["reference"]], supplier_ids.get(it["owner"]), plan[0], plan[1], it["reference"]))
        print(f"== Stocks initiaux et consommations simulés ({len(moves)} articles)")
        S.parallel(moves, S.move_stock, "mouvements")

    # Historique d'achats imaginaire (module IA)
    if ARGS.history > 0:
        selection = [it for it in items if it["reference"] in fresh][:ARGS.history]
        hrng = random.Random(ARGS.seed + 1)
        lines = []
        for it in selection:
            lines += S.purchase_history(it, [it["owner"]], hrng, ARGS.months, date.today())
        print(f"== Historique d'achats simulé : {len(selection)} articles x {ARGS.months} mois")
        imported = 0
        for i in range(0, len(lines), 2000):
            status, body = S.call("POST", "/api/procurement/prices/purchases/import",
                                  {"csv": "\n".join(lines[i:i + 2000])})
            if status != 200:
                print(f"  import refusé ({status}) : {body}")
                break
            imported += body["imported"]
        print(f"  {imported} achats importés")
        if imported:
            status, _ = S.call("POST", "/api/procurement/recommendations/refresh")
            print(f"  recalcul des prédictions et recommandations : {status}")

    print(f"\nTerminé en {(time.time() - started) / 60:.1f} min : {S.stats['created']} créations, "
          f"{S.stats['existing']} déjà présents, {S.stats['errors']} erreurs.")
    if S.stats["errors"]:
        sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Catalogue réel du marché tunisien (Open Food Facts) pour Makzeo.")
    parser.add_argument("--gateway", default=os.environ.get("MAKZEO_URL", "http://localhost:8080"))
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"))
    parser.add_argument("--count", type=int, default=600, help="nombre d'articles réels (défaut : 600)")
    parser.add_argument("--history", type=int, default=200, help="articles avec historique d'achats simulé")
    parser.add_argument("--months", type=int, default=24)
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--skip-stock", action="store_true")
    parser.add_argument("--seed", type=int, default=2026)
    parser.add_argument("--cache", default=str(HERE / "off_tunisie.json"), help="cache du téléchargement")
    parser.add_argument("--prices", help="CSV de prix relevés (code;prix_vente;prix_achat)")
    parser.add_argument("--export-template", help="exporter un modèle CSV de relevé de prix")
    parser.add_argument("--dry-run", action="store_true", help="préparer le catalogue sans appeler Makzeo")
    ARGS = parser.parse_args()
    if not ARGS.dry_run and not ARGS.admin_password:
        parser.error("mot de passe admin manquant : --admin-password ou variable ADMIN_PASSWORD")
    main()
