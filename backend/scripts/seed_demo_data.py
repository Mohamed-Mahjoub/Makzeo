#!/usr/bin/env python3
"""
Jeu de données de démonstration pour le tableau de bord.

Crée 4 fournisseurs, ~24 produits et simule 45 jours d'activité (entrées fournisseurs,
sorties quotidiennes, ruptures, réapprovisionnements) via l'API REST, puis antidate les
mouvements (UPDATE SQL sur stock_db) pour obtenir des courbes réalistes sur 45 jours.

Usage (services démarrés, base neuve de préférence) :
    python scripts/seed_demo_data.py
    python scripts/seed_demo_data.py --sql-cmd "docker exec -i stock-postgres psql -U postgres -d stock_db"
    python scripts/seed_demo_data.py --sql-cmd "psql -h localhost -U postgres -d stock_db"
    python scripts/seed_demo_data.py --gateway http://localhost:8080

Si la commande SQL échoue, le script affiche la requête à exécuter dans pgAdmin (base stock_db).
Bibliothèque standard uniquement.
"""
import argparse
import json
import random
import shlex
import subprocess
import sys
import urllib.error
import urllib.request

PORTS = {"auth": 8081, "product": 8082, "supplier": 8083, "stock": 8084}
DAYS = 45

SUPPLIERS = [
    # code, raison sociale, ville, pays (ISO 3166), identifiant fiscal
    ("F-BUR", "Maroc Bureautique SARL", "Casablanca", "MA", "001525836000072"),
    ("F-INFO", "Carthage Informatique", "Tunis", "TN", "1234567/A/M/000"),
    ("F-ELEC", "Lyon Électricité SAS", "Lyon", "FR", "FR40303265045"),
    ("F-QUIN", "Quincaillerie d'Oran", "Oran", "DZ", "000231004567890"),
]
# (référence, nom, catégorie, fournisseur, prix achat, prix vente, seuil, max, consommation moyenne/jour, profil)
# profil : "normal" | "rupture" (plus réapprovisionné) | "low" (réappro. tardif) | "dormant" (aucune sortie)
PRODUCTS = [
    ("INF-CLA-01", "Clavier USB AZERTY", "Informatique", "F-INFO", 85, 140, 10, 60, 1.6, "normal"),
    ("INF-SOU-01", "Souris optique sans fil", "Informatique", "F-INFO", 60, 99, 15, 80, 2.4, "normal"),
    ("INF-ECR-24", "Écran 24 pouces Full HD", "Informatique", "F-INFO", 1150, 1590, 3, 15, 0.35, "low"),
    ("INF-CLE-64", "Clé USB 64 Go", "Informatique", "F-INFO", 55, 90, 20, 120, 3.2, "normal"),
    ("INF-CAB-HD", "Câble HDMI 2 m", "Informatique", "F-INFO", 35, 65, 10, 60, 1.2, "rupture"),
    ("INF-TON-85", "Toner laser noir 85A", "Informatique", "F-INFO", 390, 520, 5, 25, 0.6, "normal"),
    ("BUR-PAP-A4", "Rame papier A4 80 g", "Fournitures de bureau", "F-BUR", 32.5, 45, 40, 250, 9.0, "normal"),
    ("BUR-STY-BL", "Stylo bille bleu (boîte de 50)", "Fournitures de bureau", "F-BUR", 48, 75, 10, 60, 1.4, "normal"),
    ("BUR-CLS-A4", "Classeur A4 dos 8 cm", "Fournitures de bureau", "F-BUR", 18, 29, 25, 150, 3.5, "low"),
    ("BUR-AGR-01", "Agrafeuse de bureau", "Fournitures de bureau", "F-BUR", 42, 69, 5, 30, 0.4, "normal"),
    ("BUR-POST-1", "Notes adhésives (lot de 12)", "Fournitures de bureau", "F-BUR", 36, 55, 15, 90, 2.1, "rupture"),
    ("BUR-ENV-C4", "Enveloppes C4 (boîte de 250)", "Fournitures de bureau", "F-BUR", 95, 140, 5, 30, 0.3, "dormant"),
    ("ELE-AMP-LED", "Ampoule LED E27 12 W", "Électricité", "F-ELEC", 14, 25, 50, 300, 6.5, "normal"),
    ("ELE-RAL-3M", "Rallonge multiprise 3 m", "Électricité", "F-ELEC", 65, 110, 10, 60, 1.1, "normal"),
    ("ELE-DIS-16", "Disjoncteur 16 A", "Électricité", "F-ELEC", 48, 79, 20, 100, 1.8, "low"),
    ("ELE-CAB-25", "Câble électrique 2,5 mm² (100 m)", "Électricité", "F-ELEC", 520, 690, 3, 12, 0.25, "normal"),
    ("ELE-PRI-2P", "Prise murale 2P+T", "Électricité", "F-ELEC", 22, 39, 30, 150, 2.8, "normal"),
    ("QUI-VIS-40", "Vis à bois 4x40 (boîte de 200)", "Quincaillerie", "F-QUIN", 28, 45, 20, 120, 2.2, "normal"),
    ("QUI-CHE-08", "Chevilles 8 mm (sachet de 100)", "Quincaillerie", "F-QUIN", 19, 32, 20, 120, 2.0, "normal"),
    ("QUI-MAR-01", "Marteau de charpentier", "Quincaillerie", "F-QUIN", 75, 120, 5, 25, 0.3, "dormant"),
    ("QUI-CAD-45", "Cadenas laiton 45 mm", "Quincaillerie", "F-QUIN", 55, 89, 8, 40, 0.5, "normal"),
    ("QUI-SIL-01", "Silicone sanitaire blanc", "Quincaillerie", "F-QUIN", 26, 42, 15, 80, 1.7, "rupture"),
    ("QUI-TOU-01", "Tournevis cruciforme PH2", "Quincaillerie", "F-QUIN", 22, 38, 10, 50, 0.8, "normal"),
    ("QUI-GAN-L", "Gants de travail taille L", "Quincaillerie", "F-QUIN", 18, 30, 20, 100, 2.6, "low"),
]


def base(service):
    return ARGS.gateway.rstrip("/") if ARGS.gateway else f"http://localhost:{PORTS[service]}"


def call(method, service, path, body=None):
    req = urllib.request.Request(base(service) + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, json.loads(raw) if raw else None


def main():
    global TOKEN
    rnd = random.Random(2026)
    TOKEN = None
    status, body = call("POST", "auth", "/api/auth/login", {"username": "admin", "password": ARGS.admin_password})
    if status != 200:
        sys.exit(f"Connexion impossible ({status}) : {body}")
    TOKEN = body["accessToken"]

    _, cats = call("GET", "product", "/api/categories")
    category_ids = {c["name"]: c["id"] for c in cats}

    supplier_ids = {}
    for code, name, city, country, tax_id in SUPPLIERS:
        status, s = call("POST", "supplier", "/api/suppliers", {"code": code, "name": name, "city": city,
                                                                 "country": country, "taxId": tax_id,
                                                                 "email": f"contact@{code.lower()}.example"})
        if status == 409:
            _, page = call("GET", "supplier", f"/api/suppliers?q={code}")
            s = next(x for x in page["content"] if x["code"] == code)
        supplier_ids[code] = s["id"]
    print(f"Fournisseurs : {len(supplier_ids)}")

    products = []
    for ref, name, cat, sup, pa, pv, smin, smax, rate, profile in PRODUCTS:
        status, p = call("POST", "product", "/api/products", {
            "reference": ref, "name": name, "categoryId": category_ids[cat], "supplierId": supplier_ids[sup],
            "purchasePrice": pa, "salePrice": pv, "minStock": smin, "maxStock": smax, "unit": "PIECE"})
        if status == 409:
            _, p = call("GET", "product", f"/api/products/reference/{ref}")
        products.append({"id": p["id"], "ref": ref, "sup": supplier_ids[sup], "min": smin, "max": smax,
                         "rate": rate, "profile": profile, "qty": 0})
    print(f"Produits : {len(products)}")

    count = 0

    def move(p, kind, qty, day):
        nonlocal count
        count += 1
        prefix = "BL" if kind == "IN" else "BS"
        body = {"productId": p["id"], "type": kind, "quantity": qty,
                "documentReference": f"{prefix}-DEMO-J{day}-{count:05d}",
                "reason": "Réception fournisseur" if kind == "IN" else "Consommation service"}
        if kind == "IN":
            body["supplierId"] = p["sup"]
        status, res = call("POST", "stock", "/api/stock/movements", body)
        if status == 201:
            p["qty"] = res["quantityAfter"]
        return status

    # Stock initial il y a DAYS jours
    for p in products:
        move(p, "IN", p["max"] if p["profile"] != "low" else int(p["max"] * 0.6), DAYS)
    # Simulation jour par jour (du plus ancien au plus récent)
    for day in range(DAYS - 1, -1, -1):
        for p in products:
            if p["profile"] == "dormant":
                continue
            demand = max(0, round(rnd.gauss(p["rate"], p["rate"] * 0.5)))
            if demand and p["qty"] > 0:
                move(p, "OUT", min(demand, p["qty"]), day)
            # Réapprovisionnement quand le stock passe sous le seuil
            stop_restock = (p["profile"] == "rupture" and day < 20) or (p["profile"] == "low" and day < 12)
            if p["qty"] <= p["min"] and not stop_restock and rnd.random() < 0.5:
                move(p, "IN", p["max"] - p["qty"], day)
        if day % 10 == 0:
            print(f"  J-{day} : {count} mouvements")
    print(f"Mouvements créés : {count}")

    sql = ("UPDATE stock_movements SET occurred_at = date_trunc('day', now()) "
           "- (substring(document_reference from 'DEMO-J([0-9]+)')::int * interval '1 day') "
           "+ interval '8 hours' + ((id % 540) * interval '1 minute') "
           "WHERE document_reference LIKE '%DEMO-J%';")
    try:
        subprocess.run(shlex.split(ARGS.sql_cmd) + ["-c", sql], check=True, capture_output=True, text=True)
        print("Mouvements antidatés sur", DAYS, "jours.")
    except (OSError, subprocess.CalledProcessError) as e:
        print("\n[!] Impossible d'antidater automatiquement les mouvements :", getattr(e, "stderr", e))
        print("    Exécutez cette requête dans pgAdmin sur la base stock_db :\n")
        print("   ", sql)
    print("\nTerminé : ouvrez le tableau de bord (période 30 ou 90 jours).")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", help="URL de la passerelle (sinon ports 8081-8084 en direct)")
    parser.add_argument("--admin-password", default="Admin@12345")
    parser.add_argument("--sql-cmd", default="docker exec -i stock-postgres psql -U postgres -d stock_db",
                        help="commande psql vers stock_db")
    ARGS = parser.parse_args()
    TOKEN = None
    main()
