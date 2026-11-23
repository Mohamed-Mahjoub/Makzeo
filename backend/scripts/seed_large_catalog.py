#!/usr/bin/env python3
"""
Jeu de données de démonstration Makzeo : 10 000 articles (par défaut) répartis dans 20 catégories,
40 fournisseurs tunisiens, stocks initiaux, consommations, alertes de seuil et de rupture, et
24 mois d'historique d'achats pour une sélection d'articles (module IA achats).

Tout passe par l'API REST (via la passerelle ou l'Ingress) : les règles métier, les codes-barres
EAN-13, les niveaux de stock et les alertes sont produits par l'application elle-même.

Le script est rejouable : les éléments déjà présents (même référence ou même code) sont ignorés.
Toutes les données créées portent le préfixe DEMO (références, codes fournisseurs).

Exemples :
    ADMIN_PASSWORD='...' python3 backend/scripts/seed_large_catalog.py
    python3 backend/scripts/seed_large_catalog.py --gateway http://makzeo.192.168.80.11.nip.io --count 2000
    python3 backend/scripts/seed_large_catalog.py --dry-run --export-csv demo.csv   # aperçu sans appel réseau
"""
import argparse
import csv
import json
import math
import os
import random
import sys
import threading
import time
import unicodedata
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import date

PREFIX = "DEMO"

# --------------------------------------------------------------------------------------------
# Référentiel : catégories -> familles d'articles
# (nom, unité, prix d'achat min, prix d'achat max en TND, variantes)
# --------------------------------------------------------------------------------------------
CATALOG = {
    "Fournitures de bureau": ("BUR", [
        ("Stylo bille", "PIECE", 0.35, 2.5, ["bleu", "noir", "rouge", "vert", "4 couleurs"]),
        ("Surligneur", "PIECE", 0.9, 3.2, ["jaune", "rose", "vert", "orange", "pastel"]),
        ("Agrafeuse", "PIECE", 6, 45, ["24/6", "26/6", "pince", "grande capacité", "électrique"]),
        ("Classeur à levier", "PIECE", 3.5, 9, ["dos 50 mm", "dos 75 mm", "dos 80 mm", "A4", "A4 maxi"]),
        ("Chemise cartonnée", "PAQUET", 4, 16, ["paquet de 50", "paquet de 100", "à rabats", "à élastique", "kraft"]),
        ("Bloc-notes", "PIECE", 1.2, 6.5, ["A4 ligné", "A5 quadrillé", "A6", "spirale", "repositionnable"]),
        ("Ciseaux de bureau", "PIECE", 2.5, 14, ["17 cm", "21 cm", "25 cm", "gaucher", "titane"]),
        ("Calculatrice", "PIECE", 12, 95, ["de bureau 12 chiffres", "scientifique", "imprimante", "solaire", "de poche"]),
    ]),
    "Papeterie": ("PAP", [
        ("Rame papier", "PAQUET", 11, 38, ["A4 80 g", "A4 90 g", "A3 80 g", "couleur", "recyclé"]),
        ("Enveloppe", "BOITE", 9, 42, ["DL blanche", "C4 kraft", "C5 à fenêtre", "bulle", "auto-adhésive"]),
        ("Cahier", "PIECE", 0.8, 7.5, ["96 pages", "192 pages", "grand format", "TP", "spirale"]),
        ("Étiquettes adhésives", "BOITE", 14, 65, ["A4 x 24", "A4 x 65", "transparentes", "rondes", "pour CD"]),
        ("Papier photo", "PAQUET", 18, 75, ["10x15 brillant", "A4 mat", "A4 brillant", "A3", "satiné"]),
    ]),
    "Informatique": ("INF", [
        ("Souris", "PIECE", 9, 120, ["filaire USB", "sans fil", "ergonomique", "Bluetooth", "gamer"]),
        ("Clavier", "PIECE", 18, 260, ["AZERTY filaire", "AZERTY sans fil", "arabe/français", "mécanique", "compact"]),
        ("Clé USB", "PIECE", 9, 95, ["16 Go", "32 Go", "64 Go", "128 Go", "256 Go"]),
        ("Disque SSD", "PIECE", 95, 650, ["240 Go", "480 Go", "1 To", "2 To", "NVMe 1 To"]),
        ("Câble HDMI", "PIECE", 6, 55, ["1,5 m", "3 m", "5 m", "10 m", "4K"]),
        ("Écran", "PIECE", 290, 1800, ["22 pouces", "24 pouces", "27 pouces", "27 pouces 4K", "incurvé 32 pouces"]),
        ("Onduleur", "PIECE", 190, 1500, ["650 VA", "850 VA", "1000 VA", "1500 VA", "3000 VA"]),
        ("Casque audio", "PIECE", 25, 320, ["filaire", "USB", "Bluetooth", "réduction de bruit", "call center"]),
    ]),
    "Consommables d'impression": ("IMP", [
        ("Cartouche d'encre", "PIECE", 28, 145, ["noire", "cyan", "magenta", "jaune", "pack 4 couleurs"]),
        ("Toner laser", "PIECE", 85, 520, ["noir", "cyan", "magenta", "jaune", "haute capacité"]),
        ("Tambour", "PIECE", 120, 480, ["noir", "couleur", "standard", "haute capacité", "kit"]),
        ("Ruban d'impression", "PIECE", 9, 40, ["matriciel", "thermique", "étiqueteuse 12 mm", "étiqueteuse 24 mm", "caisse"]),
    ]),
    "Électricité": ("ELE", [
        ("Câble électrique", "METRE", 0.9, 9, ["1,5 mm²", "2,5 mm²", "4 mm²", "6 mm²", "souple"]),
        ("Disjoncteur", "PIECE", 9, 95, ["10 A", "16 A", "20 A", "32 A", "différentiel 30 mA"]),
        ("Prise murale", "PIECE", 3.5, 28, ["2P+T", "double", "étanche", "USB", "encastrée"]),
        ("Ampoule LED", "PIECE", 2.8, 26, ["E27 9 W", "E27 12 W", "E14 6 W", "GU10", "tube 120 cm"]),
        ("Rallonge multiprise", "PIECE", 9, 65, ["3 prises", "4 prises", "5 prises", "parafoudre", "enrouleur 25 m"]),
        ("Projecteur LED", "PIECE", 25, 260, ["20 W", "50 W", "100 W", "détecteur", "solaire"]),
    ]),
    "Plomberie": ("PLO", [
        ("Tube PVC", "METRE", 1.8, 14, ["Ø 32", "Ø 40", "Ø 50", "Ø 100", "pression"]),
        ("Robinet", "PIECE", 15, 240, ["mitigeur lavabo", "mitigeur évier", "d'arrêt", "temporisé", "extérieur"]),
        ("Raccord laiton", "PIECE", 1.5, 18, ["coude", "té", "manchon", "réduction", "union"]),
        ("Flexible", "PIECE", 4, 32, ["30 cm", "50 cm", "douche 1,5 m", "inox", "gaz"]),
        ("Siphon", "PIECE", 6, 45, ["lavabo", "évier", "douche", "extra-plat", "chromé"]),
    ]),
    "Quincaillerie": ("QUI", [
        ("Vis à bois", "BOITE", 3.5, 28, ["3x20", "4x30", "4x40", "5x50", "6x80"]),
        ("Cheville", "BOITE", 3, 22, ["6 mm", "8 mm", "10 mm", "à frapper", "placo"]),
        ("Cadenas", "PIECE", 6, 75, ["30 mm", "40 mm", "50 mm", "à code", "laiton"]),
        ("Charnière", "PIECE", 1.2, 18, ["acier", "inox", "invisible", "piano", "porte lourde"]),
        ("Serrure", "PIECE", 18, 260, ["encastrée", "en applique", "3 points", "cylindre 30/30", "électrique"]),
        ("Poignée de porte", "PIECE", 9, 120, ["chromée", "inox brossé", "noire", "béquille", "bouton"]),
    ]),
    "Outillage": ("OUT", [
        ("Tournevis", "PIECE", 2.5, 35, ["plat", "cruciforme", "jeu de 6", "électricien", "à cliquet"]),
        ("Marteau", "PIECE", 9, 65, ["300 g", "500 g", "de coffreur", "massette", "anti-rebond"]),
        ("Perceuse", "PIECE", 95, 850, ["filaire 600 W", "visseuse 12 V", "visseuse 18 V", "à percussion", "perforateur SDS"]),
        ("Mètre ruban", "PIECE", 3, 38, ["3 m", "5 m", "8 m", "10 m", "laser"]),
        ("Clé plate", "PIECE", 3.5, 120, ["8 mm", "10 mm", "13 mm", "jeu de 8", "à molette"]),
        ("Niveau à bulle", "PIECE", 7, 95, ["40 cm", "60 cm", "100 cm", "magnétique", "laser"]),
        ("Pince", "PIECE", 8, 75, ["universelle", "coupante", "à bec", "multiprise", "à sertir"]),
    ]),
    "Peinture et revêtements": ("PEI", [
        ("Peinture acrylique", "LITRE", 6, 28, ["blanc mat", "blanc satiné", "gris perle", "beige", "façade"]),
        ("Vernis", "LITRE", 14, 55, ["incolore", "chêne", "noyer", "marin", "parquet"]),
        ("Rouleau de peinture", "PIECE", 4, 32, ["180 mm", "230 mm", "anti-goutte", "laqueur", "kit complet"]),
        ("Pinceau", "PIECE", 1.5, 22, ["25 mm", "40 mm", "60 mm", "à rechampir", "jeu de 5"]),
        ("Enduit", "KG", 1.2, 7, ["de rebouchage", "de lissage", "de façade", "prêt à l'emploi", "en poudre"]),
    ]),
    "Entretien et hygiène": ("HYG", [
        ("Liquide vaisselle", "LITRE", 1.6, 9, ["citron", "pomme", "concentré", "écologique", "professionnel"]),
        ("Javel", "LITRE", 0.8, 4.5, ["12°", "parfumée", "gel", "concentrée", "bidon 5 L"]),
        ("Papier toilette", "PAQUET", 4.5, 22, ["6 rouleaux", "12 rouleaux", "jumbo", "double épaisseur", "recyclé"]),
        ("Essuie-tout", "PAQUET", 3.5, 18, ["2 rouleaux", "4 rouleaux", "dévidoir", "industriel", "écologique"]),
        ("Savon liquide", "LITRE", 3, 16, ["neutre", "antibactérien", "lavande", "recharge 5 L", "mousse"]),
        ("Sac poubelle", "PAQUET", 2.5, 18, ["30 L", "50 L", "100 L", "renforcé", "à liens"]),
        ("Gants de ménage", "PAQUET", 1.8, 12, ["S", "M", "L", "nitrile", "latex"]),
    ]),
    "Emballage": ("EMB", [
        ("Carton d'emballage", "PIECE", 0.9, 6, ["30x20x20", "40x30x30", "60x40x40", "double cannelure", "archive"]),
        ("Ruban adhésif", "PIECE", 1.2, 8, ["transparent 48 mm", "marron 48 mm", "imprimé fragile", "toilé", "double face"]),
        ("Film étirable", "PIECE", 14, 55, ["manuel 17 µ", "manuel 23 µ", "machine", "noir", "mini rouleau"]),
        ("Papier bulle", "PIECE", 18, 85, ["50 cm x 50 m", "100 cm x 50 m", "petites bulles", "grosses bulles", "antistatique"]),
        ("Palette", "PIECE", 18, 65, ["Europe 80x120", "standard 100x120", "plastique", "demi-palette", "perdue"]),
    ]),
    "Équipements de protection": ("EPI", [
        ("Casque de chantier", "PIECE", 8, 55, ["blanc", "jaune", "ventilé", "avec visière", "électricien"]),
        ("Gants de protection", "PIECE", 2.5, 28, ["manutention", "anti-coupure", "soudeur", "chimique", "hiver"]),
        ("Chaussures de sécurité", "PIECE", 55, 260, ["S1P basses", "S3 montantes", "taille 40", "taille 42", "taille 44"]),
        ("Lunettes de protection", "PIECE", 3.5, 38, ["incolores", "teintées", "masque", "anti-buée", "sur-lunettes"]),
        ("Gilet haute visibilité", "PIECE", 4, 25, ["jaune", "orange", "taille M", "taille L", "taille XL"]),
        ("Masque respiratoire", "BOITE", 12, 85, ["FFP1", "FFP2", "FFP3", "à valve", "chirurgical"]),
    ]),
    "Mobilier de bureau": ("MOB", [
        ("Chaise de bureau", "PIECE", 120, 950, ["opérateur", "ergonomique", "visiteur", "direction", "mesh"]),
        ("Bureau", "PIECE", 280, 1900, ["120 cm", "140 cm", "160 cm", "angle", "réglable en hauteur"]),
        ("Armoire", "PIECE", 380, 1600, ["haute 2 portes", "basse", "à rideaux", "vestiaire", "métallique"]),
        ("Caisson", "PIECE", 140, 520, ["3 tiroirs", "mobile", "à dossiers suspendus", "métallique", "bois"]),
        ("Lampe de bureau", "PIECE", 18, 160, ["LED", "articulée", "à pince", "dimmable", "USB"]),
    ]),
    "Épicerie": ("EPI2", [
        ("Huile d'olive", "LITRE", 14, 38, ["vierge extra", "bio", "bidon 5 L", "bouteille 1 L", "Chemlali"]),
        ("Couscous", "KG", 1.4, 6, ["fin", "moyen", "complet", "orge", "sac 5 kg"]),
        ("Pâtes alimentaires", "KG", 1.1, 4.5, ["spaghetti", "penne", "macaroni", "langue d'oiseau", "complètes"]),
        ("Harissa", "BOITE", 1.2, 6.5, ["boîte 135 g", "boîte 380 g", "tube", "artisanale", "piquante"]),
        ("Thon à l'huile", "BOITE", 2.8, 9.5, ["80 g", "160 g", "400 g", "à l'huile d'olive", "en morceaux"]),
        ("Sucre", "KG", 1.25, 3.5, ["en poudre", "en morceaux", "roux", "sac 50 kg", "glace"]),
        ("Café moulu", "KG", 22, 65, ["arabica", "robusta", "turc", "décaféiné", "en grains"]),
    ]),
    "Boissons": ("BOI", [
        ("Eau minérale", "CARTON", 3.5, 9, ["0,5 L x 12", "1,5 L x 6", "gazeuse", "5 L", "bouteille en verre"]),
        ("Jus de fruits", "CARTON", 8, 26, ["orange", "pêche", "multifruits", "fraise", "sans sucre"]),
        ("Boisson gazeuse", "CARTON", 7, 24, ["cola", "citron", "orange", "canette x 24", "1 L x 6"]),
        ("Thé", "BOITE", 2.5, 18, ["vert", "noir", "à la menthe", "sachets x 100", "en vrac"]),
    ]),
    "Pièces automobiles": ("AUT", [
        ("Filtre à huile", "PIECE", 9, 48, ["essence", "diesel", "utilitaire", "à cartouche", "renforcé"]),
        ("Plaquettes de frein", "PIECE", 38, 190, ["avant", "arrière", "céramique", "utilitaire", "sport"]),
        ("Batterie", "PIECE", 165, 520, ["45 Ah", "60 Ah", "70 Ah", "95 Ah", "start-stop"]),
        ("Huile moteur", "LITRE", 9, 38, ["5W30", "5W40", "10W40", "15W40", "synthèse"]),
        ("Balai d'essuie-glace", "PIECE", 9, 55, ["45 cm", "55 cm", "60 cm", "plat", "arrière"]),
        ("Ampoule de phare", "PIECE", 3.5, 65, ["H4", "H7", "H1", "LED", "xénon"]),
    ]),
    "Électroménager": ("ELM", [
        ("Bouilloire", "PIECE", 32, 190, ["1,7 L", "inox", "température réglable", "sans fil", "verre"]),
        ("Climatiseur", "PIECE", 950, 3600, ["9000 BTU", "12000 BTU", "18000 BTU", "24000 BTU", "inverter"]),
        ("Ventilateur", "PIECE", 38, 320, ["sur pied", "de table", "colonne", "mural", "brumisateur"]),
        ("Réfrigérateur", "PIECE", 750, 3200, ["170 L", "250 L", "350 L", "combiné", "américain"]),
        ("Micro-ondes", "PIECE", 190, 650, ["20 L", "25 L", "grill", "combiné", "encastrable"]),
    ]),
    "Textile professionnel": ("TEX", [
        ("Blouse de travail", "PIECE", 18, 75, ["blanche", "bleue", "taille M", "taille L", "taille XL"]),
        ("Combinaison", "PIECE", 32, 140, ["coton", "jetable", "hiver", "haute visibilité", "ignifugée"]),
        ("Tablier", "PIECE", 6, 45, ["cuisine", "cuir", "imperméable", "jetable x 100", "brodé"]),
        ("Pantalon de travail", "PIECE", 28, 110, ["taille 40", "taille 42", "taille 44", "multipoches", "stretch"]),
    ]),
    "Jardinage": ("JAR", [
        ("Tuyau d'arrosage", "PIECE", 18, 140, ["15 m", "25 m", "50 m", "extensible", "renforcé"]),
        ("Terreau", "PIECE", 6, 28, ["sac 20 L", "sac 50 L", "universel", "plantes vertes", "semis"]),
        ("Sécateur", "PIECE", 12, 120, ["bypass", "à enclume", "à crémaillère", "électrique", "ergonomique"]),
        ("Engrais", "KG", 2.5, 16, ["universel", "gazon", "organique", "agrumes", "oliviers"]),
        ("Goutte-à-goutte", "PIECE", 9, 95, ["kit 20 plantes", "tuyau 16 mm", "programmateur", "goutteurs x 50", "micro-asperseur"]),
    ]),
    "Matériaux de construction": ("MAT", [
        ("Ciment", "PIECE", 11, 18, ["CEM I 42,5 sac 50 kg", "CEM II 32,5 sac 50 kg", "blanc", "colle carrelage", "prompt"]),
        ("Brique", "PIECE", 0.35, 1.6, ["12 trous", "8 trous", "pleine", "plâtrière", "réfractaire"]),
        ("Fer à béton", "METRE", 1.1, 6.5, ["Ø 8", "Ø 10", "Ø 12", "Ø 14", "Ø 16"]),
        ("Plaque de plâtre", "PIECE", 14, 38, ["BA13 standard", "hydrofuge", "coupe-feu", "phonique", "BA10"]),
        ("Carrelage", "METRE", 18, 120, ["sol 45x45", "sol 60x60", "mural 25x40", "grès cérame", "extérieur"]),
    ]),
}

BRANDS = ["Sotupa", "Carthago", "Medina Pro", "Atlas", "Hannibal", "Dougga", "Kairouan Line", "Zitouna",
          "Elissa", "Jasmin", "Bizerta", "Sfax Indus", "Tabarka", "Djerba", "Nabeul Tech", "Majestic",
          "ProLine", "Optima", "Primo", "Equinox"]

SUPPLIER_NAMES = [
    ("Société Tunisienne de Fournitures", "Tunis"), ("Carthage Distribution", "Tunis"),
    ("Sfax Industrie Équipements", "Sfax"), ("Sahel Bureautique", "Sousse"), ("Nabeul Matériaux", "Nabeul"),
    ("Bizerte Quincaillerie Générale", "Bizerte"), ("Monastir Hygiène Services", "Monastir"),
    ("Kairouan Agro Distribution", "Kairouan"), ("Gabès Électricité", "Gabès"), ("Ariana Informatique", "Ariana"),
    ("Ben Arous Outillage", "Ben Arous"), ("Manouba Emballages", "La Manouba"), ("Mahdia Textiles Pro", "Mahdia"),
    ("Djerba Commerce", "Médenine"), ("Gafsa Matériaux", "Gafsa"), ("Béja Agri Plus", "Béja"),
    ("Jendouba Distribution", "Jendouba"), ("Le Kef Fournitures", "Le Kef"), ("Siliana Négoce", "Siliana"),
    ("Zaghouan Industrie", "Zaghouan"), ("Tozeur Équipements", "Tozeur"), ("Kébili Trading", "Kébili"),
    ("Tataouine Services", "Tataouine"), ("Kasserine Bâtiment", "Kasserine"), ("Sidi Bouzid Agro", "Sidi Bouzid"),
    ("Médina Office", "Tunis"), ("Lac Tech Solutions", "Tunis"), ("Sousse Auto Pièces", "Sousse"),
    ("Hammamet Électroménager", "Nabeul"), ("Mégrine Papeterie", "Ben Arous"), ("Charguia Sécurité", "Tunis"),
    ("Enfidha Logistique", "Sousse"), ("Msaken Plomberie", "Sousse"), ("Moknine Peintures", "Monastir"),
    ("Menzel Bourguiba Métaux", "Bizerte"), ("La Marsa Mobilier", "Tunis"), ("Radès Boissons", "Ben Arous"),
    ("Zarzis Jardin", "Médenine"), ("Ksar Hellal Textile", "Monastir"), ("Grombalia Épicerie Fine", "Nabeul"),
]

local = threading.local()
lock = threading.Lock()
stats = {"created": 0, "existing": 0, "errors": 0}
TOKEN = {"value": None, "at": 0.0}


# --------------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------------
def call(method, path, body=None, retry=True):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(ARGS.gateway.rstrip("/") + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if TOKEN["value"]:
        req.add_header("Authorization", "Bearer " + TOKEN["value"])
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            content = resp.read()
            return resp.status, (json.loads(content) if content else None)
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            parsed = json.loads(content) if content else None
        except ValueError:
            parsed = content.decode(errors="replace")[:300]
        if e.code == 401 and retry and path != "/api/auth/login":
            login()  # jeton expiré pendant un long chargement
            return call(method, path, body, retry=False)
        if e.code in (502, 503, 504) and retry:
            time.sleep(2)
            return call(method, path, body, retry=False)
        return e.code, parsed
    except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
        if retry:
            time.sleep(2)
            return call(method, path, body, retry=False)
        return 0, str(e)


def login():
    with lock:
        if TOKEN["value"] and time.time() - TOKEN["at"] < 5:
            return  # un autre fil vient de se reconnecter
        TOKEN["value"] = None
        status, body = call("POST", "/api/auth/login", {"username": ARGS.admin_user, "password": ARGS.admin_password},
                            retry=False)
        if status != 200:
            sys.exit(f"Connexion impossible ({status}) : {body}")
        TOKEN["value"], TOKEN["at"] = body["accessToken"], time.time()


def parallel(items, fn, label):
    total, done, start = len(items), 0, time.time()
    results = []
    with ThreadPoolExecutor(max_workers=ARGS.workers) as pool:
        futures = [pool.submit(fn, it) for it in items]
        for f in as_completed(futures):
            results.append(f.result())
            done += 1
            if done % 250 == 0 or done == total:
                rate = done / max(time.time() - start, 0.001)
                print(f"\r  {label} : {done}/{total}  ({rate:.0f}/s, {stats['errors']} erreurs)   ", end="", flush=True)
    print()
    return results


def note(status, body, what):
    with lock:
        if status in (200, 201):
            stats["created"] += 1
        elif status == 409:
            stats["existing"] += 1
        else:
            stats["errors"] += 1
            if stats["errors"] <= 10:
                print(f"\n  [ERREUR {status}] {what} : {body}")


# --------------------------------------------------------------------------------------------
# Génération (déterministe : même graine -> mêmes articles)
# --------------------------------------------------------------------------------------------
def r3(x):
    return round(x + 1e-9, 3)


def generate_products(count, rng):
    families = []
    for cat, (code, items) in CATALOG.items():
        for item in items:
            families.append((cat, code, item))
    products = []
    per_family = {}
    i = 0
    while len(products) < count:
        cat, code, (name, unit, pmin, pmax, variants) = families[i % len(families)]
        i += 1
        n = per_family.get((code, name), 0)
        per_family[(code, name)] = n + 1
        variant = variants[n % len(variants)]
        brand = BRANDS[(n // len(variants)) % len(BRANDS)]
        gamme = n // (len(variants) * len(BRANDS))
        label = f"{name} {variant} {brand}" + (f" série {gamme + 1}" if gamme else "")
        # prix log-uniforme dans la fourchette de la famille
        purchase = r3(math.exp(rng.uniform(math.log(pmin), math.log(pmax))))
        sale = r3(purchase * rng.uniform(1.22, 1.65))
        expensive = purchase > 300
        min_stock = rng.choice([2, 3, 5]) if expensive else rng.choice([10, 15, 20, 25, 30, 50, 100])
        max_stock = min_stock * rng.choice([4, 5, 6, 8])
        ref = f"{PREFIX}-{code}-{len(products) + 1:05d}"
        products.append({
            "reference": ref, "name": label[:150], "category": cat, "unit": unit,
            "description": f"Article de démonstration – {cat.lower()}.",
            "purchasePrice": purchase, "salePrice": sale, "minStock": min_stock, "maxStock": max_stock,
        })
    return products


def stock_plan(product, rng):
    """Quantité initiale et consommation : 75 % normal, 12 % sous le seuil, 5 % en rupture, 8 % sans stock."""
    lo, hi = product["minStock"], product["maxStock"]
    roll = rng.random()
    if roll < 0.75:
        initial, profile = rng.randint(lo + 1, hi), "normal"
    elif roll < 0.87:
        initial, profile = rng.randint(max(1, lo // 2), lo) + rng.randint(5, 20), "low"
    elif roll < 0.92:
        initial, profile = rng.randint(5, 40), "out"
    else:
        return None
    if profile == "low":
        out = initial - rng.randint(max(1, lo // 2), lo)  # finit sous le seuil minimum
    elif profile == "out":
        out = initial  # rupture
    else:
        out = int(initial * rng.uniform(0.05, 0.35)) if rng.random() < 0.6 else 0
    return initial, max(0, out)


def purchase_history(product, supplier_names, rng, months, today):
    """Achats mensuels : tendance (certains articles en forte hausse), saisonnalité et bruit."""
    profile = rng.random()
    if profile < 0.25:
        trend = rng.uniform(0.018, 0.035)   # forte hausse : déclenche des achats anticipés
    elif profile < 0.40:
        trend = rng.uniform(-0.012, -0.003)  # baisse
    else:
        trend = rng.uniform(0.002, 0.010)   # inflation « normale »
    amplitude = rng.uniform(0.0, 0.06)
    phase = rng.uniform(0, 2 * math.pi)
    final_price = product["purchasePrice"]
    base = final_price / ((1 + trend) ** (months - 1))
    supplier = rng.choice(supplier_names)
    rows = []
    for m in range(months):
        y, mo = today.year, today.month - (months - m)
        while mo <= 0:
            mo += 12
            y -= 1
        price = base * (1 + trend) ** m * (1 + amplitude * math.sin(2 * math.pi * mo / 12 + phase))
        price *= rng.uniform(0.985, 1.015)
        if rng.random() < 0.15:
            supplier = rng.choice(supplier_names)
        qty = max(1, int(product["maxStock"] * rng.uniform(0.3, 0.8)))
        rows.append(f"{y:04d}-{mo:02d}-{rng.randint(3, 25):02d};{product['reference']};{supplier};{qty};{r3(price):.3f}")
    return rows


# --------------------------------------------------------------------------------------------
# Chargement
# --------------------------------------------------------------------------------------------
def ensure_categories():
    status, existing = call("GET", "/api/categories")
    if status != 200:
        sys.exit(f"Lecture des catégories impossible ({status}) : {existing}")
    by_name = {c["name"].lower(): c["id"] for c in existing}
    for name in CATALOG:
        if name.lower() not in by_name:
            status, body = call("POST", "/api/categories", {"name": name,
                                                            "description": "Catégorie de démonstration"})
            note(status, body, f"catégorie {name}")
            if status == 201:
                by_name[name.lower()] = body["id"]
    missing = [n for n in CATALOG if n.lower() not in by_name]
    if missing:
        sys.exit(f"Catégories non créées : {missing}")
    return {n: by_name[n.lower()] for n in CATALOG}


def ensure_suppliers(rng):
    ids = {}
    for i, (name, city) in enumerate(SUPPLIER_NAMES, start=1):
        code = f"{PREFIX}-F{i:02d}"
        ascii_name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
        slug = "".join(ch for ch in ascii_name if ch.isalnum())[:18]
        body = {"code": code, "name": name, "contactName": rng.choice(["M. Ben Salah", "Mme Trabelsi", "M. Jaziri",
                                                                       "Mme Bouazizi", "M. Gharbi", "Mme Chaabane"]),
                "email": f"commercial@{slug}.tn", "phone": f"+216 7{rng.randint(0, 9)} {rng.randint(100, 999)} {rng.randint(100, 999)}",
                "city": city, "country": "TN", "taxId": f"{rng.randint(1000000, 1999999)}{rng.choice('ABCDEFGHJKLMNP')}/A/M/000",
                "notes": "Fournisseur de démonstration"}
        status, created = call("POST", "/api/suppliers", body)
        note(status, created, f"fournisseur {code}")
        if status == 201:
            ids[name] = created["id"]
        else:
            s, page = call("GET", f"/api/suppliers?q={code}&size=5")
            for x in (page or {}).get("content", []) if s == 200 else []:
                if x["code"] == code:
                    ids[name] = x["id"]
    return ids


def create_product(args):
    p, category_id, supplier_id = args
    payload = {k: p[k] for k in ("reference", "name", "description", "unit", "purchasePrice", "salePrice",
                                 "minStock", "maxStock")}
    payload.update({"categoryId": category_id, "supplierId": supplier_id})
    status, body = call("POST", "/api/products", payload)
    note(status, body, f"produit {p['reference']}")
    if status == 201:
        return p["reference"], body["id"], True
    if status == 409:
        s, page = call("GET", f"/api/products?q={p['reference']}&size=5")
        for x in (page or {}).get("content", []) if s == 200 else []:
            if x["reference"] == p["reference"]:
                return p["reference"], x["id"], False
    return p["reference"], None, False


def move_stock(args):
    product_id, supplier_id, initial, out, ref = args
    status, body = call("POST", "/api/stock/movements", {
        "productId": product_id, "type": "IN", "quantity": initial, "supplierId": supplier_id,
        "documentReference": f"BL-{ref}", "reason": "Stock initial (démonstration)"})
    note(status, body, f"entrée {ref}")
    if status == 201 and out > 0:
        status, body = call("POST", "/api/stock/movements", {
            "productId": product_id, "type": "OUT", "quantity": out, "reason": "Consommation (démonstration)"})
        note(status, body, f"sortie {ref}")


def main():
    rng = random.Random(ARGS.seed)
    today = date.today()
    products = generate_products(ARGS.count, rng)
    supplier_names = [n for n, _ in SUPPLIER_NAMES]

    if ARGS.export_csv:
        with open(ARGS.export_csv, "w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=list(products[0].keys()), delimiter=";")
            w.writeheader()
            w.writerows(products)
        print(f"{len(products)} articles exportés dans {ARGS.export_csv}")
    if ARGS.dry_run:
        for p in products[:8]:
            print(f"  {p['reference']:<16} {p['name']:<55} {p['purchasePrice']:>10.3f} TND  ({p['category']})")
        print(f"  ... {len(products)} articles, {len(CATALOG)} catégories, {len(SUPPLIER_NAMES)} fournisseurs")
        return

    started = time.time()
    print(f"== Connexion à {ARGS.gateway}")
    login()

    print(f"== Catégories ({len(CATALOG)})")
    categories = ensure_categories()

    print(f"== Fournisseurs ({len(SUPPLIER_NAMES)})")
    suppliers = ensure_suppliers(rng)
    supplier_ids = list(suppliers.values())
    if not supplier_ids:
        sys.exit("Aucun fournisseur disponible")

    print(f"== Articles ({len(products)})")
    jobs = [(p, categories[p["category"]], supplier_ids[i % len(supplier_ids)]) for i, p in enumerate(products)]
    results = parallel(jobs, create_product, "articles")
    ids = {ref: pid for ref, pid, _ in results if pid}
    fresh = {ref for ref, pid, new in results if pid and new}

    if not ARGS.skip_stock:
        moves = []
        for i, p in enumerate(products):
            plan = stock_plan(p, rng)
            # le stock n'est initialisé que pour les articles créés lors de cette exécution (rejouable)
            if plan and p["reference"] in fresh:
                moves.append((ids[p["reference"]], supplier_ids[i % len(supplier_ids)], plan[0], plan[1], p["reference"]))
        print(f"== Stocks initiaux et consommations ({len(moves)} articles)")
        parallel(moves, move_stock, "mouvements")

    if ARGS.history > 0:
        print(f"== Historique d'achats ({ARGS.history} articles x {ARGS.months} mois) pour le module IA")
        selection = [p for p in products if p["reference"] in fresh]
        hrng = random.Random(ARGS.seed + 1)
        hrng.shuffle(selection)
        selection = selection[:ARGS.history]
        lines = []
        for p in selection:
            lines += purchase_history(p, supplier_names, hrng, ARGS.months, today)
        imported, rejected = 0, []
        for i in range(0, len(lines), 2000):
            status, body = call("POST", "/api/procurement/prices/purchases/import",
                                {"csv": "\n".join(lines[i:i + 2000])})
            if status == 200:
                imported += body["imported"]
                rejected += body["rejected"]
            else:
                print(f"  import refusé ({status}) : {body} — module IA achats absent ? (--history 0 pour l'ignorer)")
                break
        print(f"  {imported} achats importés, {len(rejected)} lignes rejetées")
        if imported:
            status, body = call("POST", "/api/procurement/recommendations/refresh")
            print(f"  recalcul des recommandations : {status} {body if status != 200 else ''}")

    elapsed = time.time() - started
    print(f"\nTerminé en {elapsed / 60:.1f} min : {stats['created']} créations, {stats['existing']} déjà présents, "
          f"{stats['errors']} erreurs.")
    if stats["errors"]:
        sys.exit(1)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Charge un catalogue de démonstration dans Makzeo via l'API.")
    parser.add_argument("--gateway", default=os.environ.get("MAKZEO_URL", "http://localhost:8080"),
                        help="URL de la passerelle ou de l'Ingress (défaut : http://localhost:8080)")
    parser.add_argument("--admin-user", default="admin")
    parser.add_argument("--admin-password", default=os.environ.get("ADMIN_PASSWORD"),
                        help="mot de passe admin (défaut : variable ADMIN_PASSWORD)")
    parser.add_argument("--count", type=int, default=10_000, help="nombre d'articles (défaut : 10000)")
    parser.add_argument("--workers", type=int, default=8, help="requêtes en parallèle (défaut : 8)")
    parser.add_argument("--history", type=int, default=300,
                        help="articles dotés d'un historique d'achats pour le module IA (0 pour ignorer)")
    parser.add_argument("--months", type=int, default=24, help="profondeur de l'historique en mois (défaut : 24)")
    parser.add_argument("--skip-stock", action="store_true", help="ne pas créer les mouvements de stock")
    parser.add_argument("--seed", type=int, default=2026, help="graine aléatoire (données reproductibles)")
    parser.add_argument("--dry-run", action="store_true", help="générer sans appeler l'API")
    parser.add_argument("--export-csv", help="exporter aussi la liste des articles en CSV")
    ARGS = parser.parse_args()
    if not ARGS.dry_run and not ARGS.admin_password:
        parser.error("mot de passe admin manquant : --admin-password ou variable d'environnement ADMIN_PASSWORD")
    if not 1 <= ARGS.count <= 100_000:
        parser.error("--count doit être compris entre 1 et 100000")
    main()
