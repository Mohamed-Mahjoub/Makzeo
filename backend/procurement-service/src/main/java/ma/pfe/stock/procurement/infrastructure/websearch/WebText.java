package ma.pfe.stock.procurement.infrastructure.websearch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture du texte des pages et des résultats de recherche, sans modèle de langage : montants en dinars,
 * TVA, e-mails, téléphones, villes, et ressemblance entre deux désignations. Tout ce qui n'est pas trouvé
 * avec certitude reste vide : aucun prix n'est deviné.
 */
public final class WebText {

    /** Montant suivi (ou précédé) d'une devise tunisienne : « 289,000 DT », « 1 299.000 TND », « TND 45,9 ». */
    private static final String AMOUNT = "(\\d{1,3}(?:[ \\u00A0\\u202F.]\\d{3})*(?:[.,]\\d{1,3})?|\\d+(?:[.,]\\d{1,3})?)";
    private static final Pattern PRICE_AFTER = Pattern.compile("(?<![\\d.,])" + AMOUNT + "\\s*(?:TND|DT|D\\.T\\.?|DTN|dinars?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE_BEFORE = Pattern.compile("\\b(?:TND|DT)\\s*" + AMOUNT, Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,120}\\.[A-Za-z]{2,10}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d+])(?:\\+216|00216)?[\\s.]?(?:[2-9]\\d)[\\s.]?\\d{3}[\\s.]?\\d{3}(?!\\d)");
    private static final List<String> CITIES = List.of("Tunis", "Ariana", "Ben Arous", "La Manouba", "Manouba",
            "La Marsa", "Le Kram", "La Goulette", "Sfax", "Sousse", "Monastir", "Mahdia", "Nabeul", "Hammamet",
            "Bizerte", "Béja", "Jendouba", "Le Kef", "Siliana", "Zaghouan", "Kairouan", "Kasserine", "Sidi Bouzid",
            "Gabès", "Médenine", "Djerba", "Zarzis", "Tataouine", "Gafsa", "Tozeur", "Kébili");
    private static final Set<String> STOPWORDS = Set.of("de", "des", "du", "la", "le", "les", "et", "en", "pour",
            "avec", "sans", "un", "une", "a", "au", "aux", "the", "for", "with", "and", "par", "sur", "prix", "tunisie",
            "pas", "cher", "achat", "vente", "meilleur", "promo", "neuf", "original", "originale", "noir");

    private WebText() {
    }

    /** Montant lu dans une chaîne de prix : virgule ou point décimal, espaces ou points pour les milliers. */
    public static Optional<BigDecimal> parseAmount(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.replaceAll("[^0-9.,]", "");
        if (s.isEmpty()) {
            return Optional.empty();
        }
        int lastComma = s.lastIndexOf(','), lastDot = s.lastIndexOf('.');
        String normalized;
        if (lastComma >= 0 && lastDot >= 0) {
            boolean commaDecimal = lastComma > lastDot;
            normalized = commaDecimal ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (lastComma >= 0) {
            normalized = s.indexOf(',') == lastComma ? s.replace(',', '.') : s.replace(",", "");
        } else {
            normalized = s.indexOf('.') == lastDot ? s : s.replace(".", "");
        }
        try {
            BigDecimal value = new BigDecimal(normalized).setScale(3, RoundingMode.HALF_UP);
            return value.signum() > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Prix en dinars trouvés dans un texte, dans l'ordre, avec leur position. */
    public static List<Price> prices(String text) {
        List<Price> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (Pattern p : List.of(PRICE_AFTER, PRICE_BEFORE)) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                parseAmount(m.group(1)).ifPresent(v -> out.add(new Price(v, m.start(), vatNear(text, m.start(), m.end()))));
            }
        }
        out.sort((a, b) -> Integer.compare(a.position(), b.position()));
        return out;
    }

    /**
     * Prix de l'article sur une page : le premier montant qui suit la mention de l'article (référence ou
     * désignation), plausible au regard du dernier prix payé s'il est connu ; sinon rien.
     */
    public static Optional<Price> productPrice(String text, List<String> anchors, BigDecimal knownPrice) {
        List<Price> all = prices(text);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        int anchor = -1;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String a : anchors) {
            if (a != null && a.length() >= 4) {
                int i = lower.indexOf(a.toLowerCase(Locale.ROOT));
                if (i >= 0 && (anchor < 0 || i < anchor)) {
                    anchor = i;
                }
            }
        }
        for (Price p : all) {
            boolean afterAnchor = anchor < 0 || p.position() >= anchor && p.position() - anchor < 4000;
            if (afterAnchor && plausible(p.amount(), knownPrice)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /** Un prix est plausible s'il reste entre le tiers et le triple du dernier prix payé (TTC compris). */
    public static boolean plausible(BigDecimal amount, BigDecimal knownPrice) {
        if (knownPrice == null || knownPrice.signum() <= 0) {
            return true;
        }
        double ratio = amount.doubleValue() / knownPrice.doubleValue();
        return ratio >= 0.33 && ratio <= 3.0;
    }

    /** TTC sauf mention « HT » accolée au montant (les boutiques en ligne affichent des prix TTC). */
    private static boolean vatNear(String text, int start, int end) {
        String after = text.substring(end, Math.min(text.length(), end + 15)).toUpperCase(Locale.ROOT);
        String before = text.substring(Math.max(0, start - 30), start).toUpperCase(Locale.ROOT);
        for (String window : List.of(after, before)) {
            if (window.matches("(?s).*\\bTTC\\b.*")) {
                return true;
            }
            if (window.matches("(?s).*\\b(HT|H\\.T\\.?|HORS TAXES?)(\\b|$).*")) {
                return false;
            }
        }
        return true;
    }

    /** Première adresse e-mail du texte, de préférence sur le domaine du site. */
    public static String email(String text, String host) {
        if (text == null) {
            return null;
        }
        Matcher m = EMAIL.matcher(text);
        String first = null;
        String base = host == null ? null : registrableName(host);
        while (m.find()) {
            String e = m.group().toLowerCase(Locale.ROOT);
            if (e.endsWith(".png") || e.endsWith(".jpg") || e.endsWith(".webp") || e.contains("example.")
                    || e.contains("sentry") || e.contains("wixpress")) {
                continue;
            }
            if (base != null && e.substring(e.indexOf('@') + 1).contains(base)) {
                return e;
            }
            if (first == null) {
                first = e;
            }
        }
        return first;
    }

    /** Premier numéro de téléphone tunisien du texte (8 chiffres, indicatif +216 facultatif). */
    public static String phone(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = PHONE.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9+]", "");
            String local = digits.replaceFirst("^(\\+216|00216)", "");
            if (local.length() == 8) {
                return "+216 " + local.substring(0, 2) + " " + local.substring(2, 5) + " " + local.substring(5);
            }
        }
        return null;
    }

    /** Ville tunisienne citée dans le texte (la première trouvée dans l'ordre de la liste). */
    public static String city(String text) {
        if (text == null) {
            return null;
        }
        String folded = fold(text);
        for (String c : CITIES) {
            if (Pattern.compile("\\b" + Pattern.quote(fold(c)) + "\\b").matcher(folded).find()) {
                return c;
            }
        }
        return null;
    }

    /** Mots significatifs d'une désignation (sans accents ni mots vides). */
    public static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String t : fold(text).split("[^a-z0-9]+")) {
            if (t.length() >= 2 && !STOPWORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Part (0-100) des mots de la désignation de référence retrouvés dans l'autre désignation. */
    public static int similarity(String reference, String candidate) {
        Set<String> ref = tokens(reference);
        if (ref.isEmpty()) {
            return 0;
        }
        Set<String> cand = tokens(candidate);
        long common = ref.stream().filter(cand::contains).count();
        return (int) Math.round(100.0 * common / ref.size());
    }

    /** Vrai si le code (référence constructeur, EAN) apparaît tel quel dans le texte, séparateurs ignorés. */
    public static boolean containsCode(String text, String code) {
        if (text == null || code == null) {
            return false;
        }
        String c = fold(code).replaceAll("[^a-z0-9]", "");
        if (c.length() < 4 || !c.matches(".*\\d.*")) {
            return false; // une référence sans chiffre (ex. « TONER ») n'identifie rien
        }
        return fold(text).replaceAll("[^a-z0-9]", "").contains(c);
    }

    /** Hôte d'une URL, sans « www. ». */
    public static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Nom d'enseigne déduit d'un domaine : « tunisianet.com.tn » → « Tunisianet ». */
    public static String nameFromHost(String host) {
        String base = registrableName(host);
        if (base == null || base.isEmpty()) {
            return host;
        }
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private static String registrableName(String host) {
        if (host == null) {
            return null;
        }
        String[] parts = host.split("\\.");
        int i = parts.length - 2;
        while (i > 0 && parts[i].matches("com|net|org|gov|edu|co")) {
            i--;
        }
        return i >= 0 ? parts[i] : host;
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Montant lu, sa position dans le texte, et s'il est TTC. */
    public record Price(BigDecimal amount, int position, boolean includesVat) {
    }
}
