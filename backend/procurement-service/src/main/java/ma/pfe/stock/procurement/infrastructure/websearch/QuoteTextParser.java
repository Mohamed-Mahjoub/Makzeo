package ma.pfe.stock.procurement.infrastructure.websearch;

import ma.pfe.stock.procurement.application.port.MarketIntelligence.ExtractedQuote;
import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.ProductInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture d'un devis collé (courriel, texte d'un PDF) sans modèle de langage : prix unitaire, TVA,
 * quantité minimale, disponibilité, délai, validité et conditions de paiement, repérés par leurs
 * libellés usuels. Le résultat pré-remplit le formulaire ; l'acheteur le vérifie avant d'enregistrer.
 */
public final class QuoteTextParser {

    private static final String NUM = "(\\d{1,3}(?:[ \\u00A0\\u202F.]\\d{3})*(?:[.,]\\d{1,3})?|\\d+(?:[.,]\\d{1,3})?)";
    private static final Pattern UNIT_PRICE = Pattern.compile(
            "\\b(?:prix\\s+unitaire|p\\.?\\s?u\\.?|prix\\s+u\\.?|prix\\s+net|prix)\\s*(?:\\(?\\s*(?:HT|TTC|H\\.T\\.?)\\s*\\)?)?\\s*[:=]?\\s*"
                    + "(?:TND|DT)?\\s*" + NUM, Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_QTY = Pattern.compile(
            "(?:quantit[ée]\\s+minimale?|minimum\\s+de\\s+commande|qt[ée]\\s+min(?:imale|imum)?\\.?|MOQ)\\D{0,15}(\\d{1,6})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEAD_DAYS = Pattern.compile(
            "(?:d[ée]lai[^\\n\\d]{0,40}|livr\\w*[^\\n\\d]{0,30}|sous\\s+)(\\d{1,3})\\s*(jours?|j\\b|semaines?|sem\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_UNTIL = Pattern.compile(
            "(?:valable|validit[ée])[^\\n\\d]{0,40}(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID_DAYS = Pattern.compile(
            "(?:valable|validit[ée])[^\\n\\d]{0,30}(\\d{1,3})\\s*jours?", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAYMENT = Pattern.compile("^.*(?:paiement|r[èe]glement)\\b.*$",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern COMPANY = Pattern.compile(
            "^\\s*([A-Z][\\w&'. -]{1,60}?\\s+(?:SARL|SA|SUARL|S\\.A\\.R\\.L\\.?|S\\.A\\.?))\\b", Pattern.MULTILINE);
    private static final List<DateTimeFormatter> DATES = List.of(DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"));

    private QuoteTextParser() {
    }

    public static Optional<ExtractedQuote> parse(String text, ProductInfo product, String currency, LocalDate today) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        // prix unitaire : libellé explicite, sinon premier montant en dinars plausible
        BigDecimal unitPrice = null;
        boolean includesVat = false;
        Matcher m = UNIT_PRICE.matcher(text);
        while (m.find() && unitPrice == null) {
            Optional<BigDecimal> v = WebText.parseAmount(m.group(1));
            if (v.isPresent() && WebText.plausible(v.get(), product.purchasePrice())) {
                unitPrice = v.get();
                includesVat = m.group().toUpperCase(Locale.ROOT).contains("TTC");
            }
        }
        if (unitPrice == null) {
            for (WebText.Price p : WebText.prices(text)) {
                if (WebText.plausible(p.amount(), product.purchasePrice())) {
                    unitPrice = p.amount();
                    includesVat = text.toUpperCase(Locale.ROOT).contains("TTC") && !text.toUpperCase(Locale.ROOT).contains(" HT");
                    break;
                }
            }
        }
        if (unitPrice == null) {
            return Optional.empty();
        }

        Integer minQty = first(MIN_QTY, text).map(Integer::valueOf).filter(q -> q >= 1).orElse(null);
        Integer lead = null;
        Matcher l = LEAD_DAYS.matcher(text);
        if (l.find()) {
            int n = Integer.parseInt(l.group(1));
            lead = l.group(2).toLowerCase(Locale.ROOT).startsWith("sem") ? n * 7 : n;
        }
        LocalDate validUntil = first(VALID_UNTIL, text).flatMap(QuoteTextParser::date)
                .or(() -> first(VALID_DAYS, text).map(d -> today.plusDays(Integer.parseInt(d))))
                .orElse(null);
        String lower = text.toLowerCase(Locale.ROOT);
        Availability availability = lower.matches("(?s).*(rupture|indisponible|non disponible|plus disponible).*")
                ? Availability.UNAVAILABLE
                : lower.matches("(?s).*(sur commande|à commander|a commander|sous \\d+ semaines?).*")
                ? Availability.ON_ORDER : Availability.IN_STOCK;
        String payment = first(PAYMENT, text, 0).map(String::trim).map(s -> cut(s, 255)).orElse(null);
        String supplier = first(COMPANY, text).map(String::trim).orElse(null);

        return Optional.of(new ExtractedQuote(supplier, null, unitPrice, currency, includesVat, minQty, availability,
                lead, payment, validUntil, "Lu automatiquement à partir du texte : à vérifier avant d'enregistrer."));
    }

    private static Optional<String> first(Pattern p, String text) {
        return first(p, text, 1);
    }

    private static Optional<String> first(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        return m.find() ? Optional.ofNullable(m.group(group)) : Optional.empty();
    }

    private static Optional<LocalDate> date(String raw) {
        for (DateTimeFormatter f : DATES) {
            try {
                return Optional.of(LocalDate.parse(raw, f));
            } catch (DateTimeParseException ignored) {
                // format suivant
            }
        }
        return Optional.empty();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
