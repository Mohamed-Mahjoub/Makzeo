#!/usr/bin/env python3
"""
Test de bout en bout (E2E) du backend Stock Management.

Pré-requis : les 5 microservices démarrés (docker compose up, ou en local).
Usage :
    python3 scripts/e2e_test.py                 # URLs locales par défaut (ports 8081..8085)
    python3 scripts/e2e_test.py --gateway http://localhost:8080   # via la passerelle (Nginx / Ingress)

Aucune dépendance externe (bibliothèque standard uniquement).
"""
import argparse
import os
import json
import sys
import time
import urllib.error
import urllib.request

PORTS = {"auth": 8081, "product": 8082, "supplier": 8083, "stock": 8084, "reporting": 8085}
RUN_ID = str(int(time.time()))[-6:]
passed = 0


def base(service):
    if ARGS.gateway:
        return ARGS.gateway.rstrip("/")
    return f"http://localhost:{PORTS[service]}"


def call(method, service, path, body=None, token=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base(service) + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read()
            if raw:
                return resp.status, content, dict(resp.headers)
            return resp.status, (json.loads(content) if content else None)
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            parsed = json.loads(content) if content else None
        except ValueError:
            parsed = content.decode(errors="replace")
        if raw:
            return e.code, content, dict(e.headers)
        return e.code, parsed


def check(label, condition, detail=""):
    global passed
    if not condition:
        print(f"  [ÉCHEC] {label} {detail}")
        sys.exit(1)
    passed += 1
    print(f"  [OK] {label}")


def login(username, password):
    status, body = call("POST", "auth", "/api/auth/login", {"username": username, "password": password})
    check(f"login {username}", status == 200, body)
    return body["accessToken"]


def main():
    print("== Authentification & utilisateurs")
    admin = login("admin", ARGS.admin_password)
    status, body = call("GET", "auth", "/api/auth/me", token=admin)
    check("GET /api/auth/me", status == 200 and body["username"] == "admin")
    status, body = call("POST", "auth", "/api/auth/login", {"username": "admin", "password": "mauvais"})
    check("mot de passe invalide -> 401", status == 401, body)
    status, _ = call("GET", "product", "/api/products")
    check("appel sans jeton -> 401", status == 401)
    status, _ = call("GET", "product", "/api/products", token="jeton.invalide.xyz")
    check("jeton invalide -> 401", status == 401)

    users = {}
    for role in ["GESTIONNAIRE", "MAGASINIER", "CONSULTANT"]:
        username = f"{role.lower()}{RUN_ID}"
        status, body = call("POST", "auth", "/api/users", {
            "username": username, "email": f"{username}@stock.local", "fullName": f"Utilisateur {role}",
            "password": "Password123", "roles": [role]}, token=admin)
        check(f"création utilisateur {role}", status == 201, body)
        users[role] = login(username, "Password123")
    status, body = call("POST", "auth", "/api/users", {"username": "x", "email": "pas-un-email",
                                                        "fullName": "", "password": "1", "roles": []}, token=admin)
    check("validation des champs -> 400 + violations", status == 400 and len(body["violations"]) >= 4, body)
    status, _ = call("GET", "auth", "/api/users", token=users["MAGASINIER"])
    check("magasinier sur /api/users -> 403", status == 403)

    print("== Fournisseurs")
    status, supplier = call("POST", "supplier", "/api/suppliers", {
        "code": f"F{RUN_ID}", "name": "Maroc Bureautique SARL", "contactName": "M. Alaoui",
        "email": "contact@marocbureau.ma", "phone": "+212 522 00 00 00", "city": "Casablanca", "country": "MA",
        "taxId": f"001{RUN_ID}000000"[:15].ljust(15, "0")}, token=users["GESTIONNAIRE"])
    check("création fournisseur", status == 201, supplier)
    status, body = call("POST", "supplier", "/api/suppliers", {"code": f"F{RUN_ID}", "name": "Doublon"},
                        token=users["GESTIONNAIRE"])
    check("code fournisseur en double -> 409", status == 409, body)
    status, _ = call("POST", "supplier", "/api/suppliers", {"code": "X1", "name": "Interdit"},
                     token=users["MAGASINIER"])
    check("magasinier crée un fournisseur -> 403", status == 403)
    status, page = call("GET", "supplier", "/api/suppliers?q=bureautique", token=users["CONSULTANT"])
    check("recherche fournisseurs", status == 200 and page["totalElements"] >= 1)
    status, tn = call("POST", "supplier", "/api/suppliers", {
        "code": f"TN{RUN_ID}", "name": "Carthage Fournitures", "city": "Tunis", "country": "TN",
        "taxId": f"{RUN_ID}7/A/M/000"}, token=users["GESTIONNAIRE"])
    check("fournisseur tunisien (matricule fiscal)", status == 201 and tn["countryName"] == "Tunisie", tn)
    status, body = call("POST", "supplier", "/api/suppliers", {"code": f"BAD{RUN_ID}", "name": "ICE invalide",
                                                                "country": "MA", "taxId": "12AB"}, token=users["GESTIONNAIRE"])
    check("ICE marocain invalide -> 400", status == 400, body)
    status, countries = call("GET", "supplier", "/api/suppliers/countries", token=users["CONSULTANT"])
    check("liste des pays (ISO 3166)", status == 200 and len(countries) > 240
          and {"code": "TN", "name": "Tunisie"} in countries)
    status, page = call("GET", "supplier", "/api/suppliers?country=TN", token=users["CONSULTANT"])
    check("filtre fournisseurs par pays", status == 200 and all(x["country"] == "TN" for x in page["content"]))

    print("== Paramètres : devise")
    status, settings = call("GET", "auth", "/api/settings", token=users["CONSULTANT"])
    check("lecture de la devise", status == 200 and len(settings["currency"]) == 3, settings)
    initial_currency = settings["currency"]
    status, currencies = call("GET", "auth", "/api/settings/currencies", token=users["CONSULTANT"])
    check("liste des devises ISO 4217", status == 200 and len(currencies) > 140
          and [c["code"] for c in currencies[:2]] == ["MAD", "TND"])
    status, _ = call("PUT", "auth", "/api/settings", {"currency": "EUR"}, token=users["GESTIONNAIRE"])
    check("seul l'administrateur change la devise -> 403", status == 403)
    status, settings = call("PUT", "auth", "/api/settings", {"currency": "TND"}, token=admin)
    check("devise = dinar tunisien (3 décimales)", status == 200 and settings["currency"] == "TND"
          and settings["currencyFractionDigits"] == 3, settings)

    print("== Catalogue (catégories, produits, codes-barres)")
    status, cat = call("POST", "product", "/api/categories", {"name": f"Consommables {RUN_ID}"},
                       token=users["GESTIONNAIRE"])
    check("création catégorie", status == 201, cat)
    status, p1 = call("POST", "product", "/api/products", {
        "reference": f"RAM-{RUN_ID}", "name": "Rame papier A4 80g", "categoryId": cat["id"], "unit": "PAQUET",
        "purchasePrice": 32.505, "salePrice": 45, "minStock": 10, "supplierId": supplier["id"]},
                      token=users["GESTIONNAIRE"])
    check("création produit (EAN-13 généré)", status == 201 and len(p1["barcode"]) == 13
          and p1["barcode"].startswith("200"), p1)
    check("prix à 3 décimales (millimes)", abs(p1["purchasePrice"] - 32.505) < 1e-9, p1)
    status, p2 = call("POST", "product", "/api/products", {
        "reference": f"STY-{RUN_ID}", "name": "Stylo bille bleu", "categoryId": cat["id"],
        "barcode": "4006381333931", "purchasePrice": 2.5, "salePrice": 4, "minStock": 50},
                      token=users["GESTIONNAIRE"])
    if status == 409:  # code-barres déjà utilisé lors d'une exécution précédente
        status, p2 = call("POST", "product", "/api/products", {
            "reference": f"STY-{RUN_ID}", "name": "Stylo bille bleu", "categoryId": cat["id"],
            "purchasePrice": 2.5, "salePrice": 4, "minStock": 50}, token=users["GESTIONNAIRE"])
    check("création produit avec EAN-13 fourni", status == 201, p2)
    status, body = call("POST", "product", "/api/products", {
        "reference": f"BAD-{RUN_ID}", "name": "Mauvais EAN", "categoryId": cat["id"], "barcode": "4006381333932",
        "minStock": 0}, token=users["GESTIONNAIRE"])
    check("EAN-13 avec mauvaise clé -> 400", status == 400, body)
    status, body = call("POST", "product", "/api/products", {
        "reference": f"RAM-{RUN_ID}", "name": "Doublon", "categoryId": cat["id"], "minStock": 0},
                        token=users["GESTIONNAIRE"])
    check("référence en double -> 409", status == 409, body)
    status, body = call("POST", "product", "/api/products", {
        "reference": f"SUP-{RUN_ID}", "name": "Fournisseur inconnu", "categoryId": cat["id"], "minStock": 0,
        "supplierId": 999999}, token=users["GESTIONNAIRE"])
    check("fournisseur inexistant (appel inter-services) -> 409", status == 409
          and body["code"] == "SUPPLIER_NOT_FOUND", body)
    status, body = call("GET", "product", f"/api/products/barcode/{p1['barcode']}", token=users["MAGASINIER"])
    check("recherche par code-barres (scan)", status == 200 and body["id"] == p1["id"])
    status, body = call("GET", "product", f"/api/products?q=RAM-{RUN_ID}", token=users["CONSULTANT"])
    check("recherche multicritère", status == 200 and body["totalElements"] == 1)
    status, body = call("DELETE", "product", f"/api/categories/{cat['id']}", token=users["GESTIONNAIRE"])
    check("suppression catégorie non vide -> 409", status == 409, body)

    print("== Mouvements de stock & alertes")
    status, body = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "OUT", "quantity": 1}, token=users["MAGASINIER"])
    check("sortie sur stock vide -> 409 INSUFFICIENT_STOCK", status == 409
          and body["code"] == "INSUFFICIENT_STOCK", body)
    status, m1 = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "IN", "quantity": 100, "supplierId": supplier["id"],
        "documentReference": f"BL-{RUN_ID}", "reason": "Réception commande"}, token=users["MAGASINIER"])
    check("entrée de stock", status == 201 and m1["quantityAfter"] == 100, m1)
    status, m2 = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "OUT", "quantity": 92, "reason": "Service comptabilité"},
                      token=users["MAGASINIER"])
    check("sortie de stock", status == 201 and m2["quantityAfter"] == 8 and m2["signedQuantity"] == -92, m2)
    status, alerts = call("GET", "stock", "/api/stock/alerts?activeOnly=true&size=200", token=users["CONSULTANT"])
    mine = [a for a in alerts["content"] if a["productId"] == p1["id"]]
    check("alerte SEUIL_MIN déclenchée (8 <= 10)", len(mine) == 1 and mine[0]["type"] == "SEUIL_MIN", mine)
    status, _ = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "OUT", "quantity": 8}, token=users["MAGASINIER"])
    status, alerts = call("GET", "stock", "/api/stock/alerts?activeOnly=true&size=200", token=users["CONSULTANT"])
    mine = [a for a in alerts["content"] if a["productId"] == p1["id"]]
    check("alerte RUPTURE remplace SEUIL_MIN", len(mine) == 1 and mine[0]["type"] == "RUPTURE", mine)
    status, ack = call("PATCH", "stock", f"/api/stock/alerts/{mine[0]['id']}/acknowledge",
                       token=users["MAGASINIER"])
    check("acquittement de l'alerte", status == 200 and ack["status"] == "ACKNOWLEDGED", ack)
    status, _ = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "IN", "quantity": 50}, token=users["MAGASINIER"])
    status, alerts = call("GET", "stock", "/api/stock/alerts?activeOnly=true&size=200", token=users["CONSULTANT"])
    mine = [a for a in alerts["content"] if a["productId"] == p1["id"]]
    check("réapprovisionnement -> alertes résolues", len(mine) == 0, mine)
    status, _ = call("POST", "stock", "/api/stock/movements", {
        "productId": p2["id"], "type": "IN", "quantity": 40}, token=users["MAGASINIER"])
    check("entrée produit 2 (40 <= seuil 50)", status == 201)
    status, _ = call("POST", "stock", "/api/stock/movements", {
        "productId": p1["id"], "type": "IN", "quantity": 5}, token=users["CONSULTANT"])
    check("consultant enregistre un mouvement -> 403", status == 403)
    status, level = call("GET", "stock", f"/api/stock/levels/{p1['id']}", token=users["CONSULTANT"])
    check("niveau de stock produit 1 = 50", status == 200 and level["quantity"] == 50 and level["status"] == "OK",
          level)
    status, hist = call("GET", "stock", f"/api/stock/movements?productId={p1['id']}", token=users["CONSULTANT"])
    check("historique des mouvements (4, plus récent en premier)", status == 200 and hist["totalElements"] == 4
          and hist["content"][0]["quantityAfter"] == 50, hist)
    status, dash = call("GET", "stock", "/api/stock/dashboard", token=users["CONSULTANT"])
    check("tableau de bord", status == 200 and dash["entriesToday"] >= 3, dash)
    status, ana = call("GET", "reporting", "/api/reports/analytics?days=30", token=users["MAGASINIER"])
    check("indicateurs décisionnels (analytics)", status == 200 and ana["kpis"]["productCount"] >= 2
          and len(ana["dailyFlows"]) == 30 and len(ana["replenishment"]) >= 2, ana if status != 200 else "")

    print("== Inventaire")
    status, inv = call("POST", "stock", "/api/stock/inventories", {"name": f"Inventaire test {RUN_ID}"},
                       token=users["MAGASINIER"])
    check("ouverture inventaire", status == 201 and inv["status"] == "IN_PROGRESS", inv)
    status, inv = call("PUT", "stock", f"/api/stock/inventories/{inv['id']}/counts",
                       {"productId": p1["id"], "countedQuantity": 47}, token=users["MAGASINIER"])
    status, inv = call("PUT", "stock", f"/api/stock/inventories/{inv['id']}/counts",
                       {"productId": p2["id"], "countedQuantity": 40}, token=users["MAGASINIER"])
    check("saisie des comptages (1 écart)", status == 200 and inv["lineCount"] == 2
          and inv["discrepancyCount"] == 1, inv)
    status, _ = call("POST", "stock", f"/api/stock/inventories/{inv['id']}/validate", token=users["MAGASINIER"])
    check("magasinier ne peut pas valider -> 403", status == 403)
    status, inv = call("POST", "stock", f"/api/stock/inventories/{inv['id']}/validate",
                       token=users["GESTIONNAIRE"])
    check("validation inventaire", status == 200 and inv["status"] == "VALIDATED", inv)
    status, level = call("GET", "stock", f"/api/stock/levels/{p1['id']}", token=users["CONSULTANT"])
    check("stock ajusté à 47", level["quantity"] == 47, level)
    status, hist = call("GET", "stock", f"/api/stock/movements?inventoryId={inv['id']}", token=admin)
    check("mouvement ADJUSTMENT tracé", hist["totalElements"] == 1 and hist["content"][0]["type"] == "ADJUSTMENT"
          and hist["content"][0]["signedQuantity"] == -3, hist)
    status, body = call("PUT", "stock", f"/api/stock/inventories/{inv['id']}/counts",
                        {"productId": p1["id"], "countedQuantity": 1}, token=users["MAGASINIER"])
    check("inventaire validé non modifiable -> 409", status == 409, body)

    print("== Rapports PDF / Excel")
    for path in ["/api/reports/stock", "/api/reports/movements", "/api/reports/alerts?activeOnly=false",
                 f"/api/reports/inventories/{inv['id']}"]:
        for fmt in ["pdf", "xlsx"]:
            sep = "&" if "?" in path else "?"
            status, content, headers = call("GET", "reporting", f"{path}{sep}format={fmt}", token=users["CONSULTANT"],
                                            raw=True)
            magic = b"%PDF" if fmt == "pdf" else b"PK"
            check(f"rapport {path.split('?')[0]} ({fmt}) - {len(content)} octets",
                  status == 200 and content.startswith(magic)
                  and "attachment" in headers.get("Content-Disposition", ""), (status, content[:200]))
            if ARGS.save_reports:
                name = path.strip("/").replace("/", "_").split("?")[0]
                with open(f"{ARGS.save_reports}/{name}.{fmt}", "wb") as f:
                    f.write(content)
    import io, zipfile
    status, content, _ = call("GET", "reporting", "/api/reports/stock?format=xlsx", token=users["CONSULTANT"], raw=True)
    shared = zipfile.ZipFile(io.BytesIO(content)).read("xl/sharedStrings.xml").decode()
    check("rapport Excel dans la devise de l'application (TND)", "Prix achat (TND)" in shared and "Valeur (TND)" in shared)
    status, ana = call("GET", "reporting", "/api/reports/analytics?days=30", token=users["CONSULTANT"])
    check("indicateurs dans la devise de l'application", ana["currency"] == "TND" and ana["currencyFractionDigits"] == 3)
    status, _ = call("PUT", "auth", "/api/settings", {"currency": initial_currency}, token=admin)
    check(f"retour à la devise initiale ({initial_currency})", status == 200)
    status, _, _ = call("GET", "reporting", "/api/reports/stock?format=pdf", token=users["MAGASINIER"], raw=True)
    check("magasinier sans droit de reporting -> 403", status == 403)
    status, body = call("GET", "reporting", "/api/reports/stock?format=docx", token=admin)
    check("format non supporté -> 400", status == 400, body)

    print(f"\n{passed} vérifications réussies.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--gateway", help="URL de la passerelle (sinon ports 8081-8085 en direct)")
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"),
                        help="mot de passe du compte admin (par défaut : variable d'environnement ADMIN_PASSWORD)")
    parser.add_argument("--save-reports", help="dossier où enregistrer les rapports générés")
    ARGS = parser.parse_args()
    if not ARGS.admin_password:
        parser.error("mot de passe admin manquant : --admin-password <mdp> ou variable d'environnement ADMIN_PASSWORD")
    main()
