package ma.pfe.stock.product.domain.model;

import java.util.regex.Pattern;

/**
 * Objet-valeur "Code-barres".
 * <ul>
 *   <li>13 chiffres : EAN-13, la clé de contrôle est vérifiée ;</li>
 *   <li>sinon : code interne compatible Code 128 (4 à 32 caractères A-Z, 0-9, '-').</li>
 * </ul>
 */
public record Barcode(String value) {

    private static final Pattern EAN13 = Pattern.compile("^\\d{13}$");
    private static final Pattern CODE128 = Pattern.compile("^[A-Z0-9-]{4,32}$");

    /** Préfixe GS1 réservé à l'usage interne (codes 200-299). */
    public static final String INTERNAL_PREFIX = "200";

    public Barcode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le code-barres ne peut pas être vide");
        }
        value = value.trim().toUpperCase();
        if (EAN13.matcher(value).matches()) {
            int expected = checkDigit(value.substring(0, 12));
            if (expected != Character.getNumericValue(value.charAt(12))) {
                throw new IllegalArgumentException("Code EAN-13 invalide (clé de contrôle attendue : " + expected + ")");
            }
        } else if (!CODE128.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Code-barres invalide : EAN-13 (13 chiffres) ou code interne de 4 à 32 caractères [A-Z0-9-]");
        }
    }

    public boolean isEan13() {
        return EAN13.matcher(value).matches();
    }

    /** Génère un EAN-13 interne à partir d'un numéro de séquence (9 chiffres max). */
    public static Barcode internalEan13(long sequence) {
        if (sequence < 0 || sequence > 999_999_999L) {
            throw new IllegalArgumentException("Séquence hors limites pour un EAN-13 interne : " + sequence);
        }
        String body = INTERNAL_PREFIX + String.format("%09d", sequence);
        return new Barcode(body + checkDigit(body));
    }

    /** Calcul de la clé EAN-13 (pondération 1/3 sur les 12 premiers chiffres). */
    public static int checkDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(twelveDigits.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    @Override
    public String toString() {
        return value;
    }
}
