package ma.pfe.stock.auth.domain.model;

import java.time.Instant;
import java.util.Currency;
import java.util.Locale;

/**
 * Paramètres globaux de l'application (une seule instance).
 * La devise est celle dans laquelle tous les prix sont saisis et affichés (pas de conversion).
 */
public class AppSettings {

    public static final String DEFAULT_CURRENCY = "MAD";

    private Currency currency;
    private Instant updatedAt;
    private String updatedBy;

    public AppSettings(String currencyCode, Instant updatedAt, String updatedBy) {
        this.currency = parse(currencyCode);
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public static AppSettings defaults() {
        return new AppSettings(DEFAULT_CURRENCY, Instant.now(), "system");
    }

    public void changeCurrency(String currencyCode, String username) {
        this.currency = parse(currencyCode);
        this.updatedAt = Instant.now();
        this.updatedBy = username;
    }

    /** Valide un code ISO 4217 (ex. TND, EUR, USD). */
    public static Currency parse(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code devise est obligatoire");
        }
        try {
            return Currency.getInstance(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Code devise ISO 4217 inconnu : " + code);
        }
    }

    public Currency getCurrency() { return currency; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
