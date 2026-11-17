package ma.pfe.stock.auth.application.dto;

import ma.pfe.stock.auth.domain.model.AppSettings;

import java.time.Instant;
import java.util.Locale;

/**
 * Paramètres exposés à l'API.
 *
 * @param currency               code ISO 4217 (ex. TND)
 * @param currencyName           libellé en français (ex. dinar tunisien)
 * @param currencySymbol         symbole (ex. TND, €, $)
 * @param currencyFractionDigits nombre de décimales de la devise (3 pour le dinar tunisien)
 */
public record SettingsView(String currency, String currencyName, String currencySymbol, int currencyFractionDigits,
                           Instant updatedAt, String updatedBy) {

    public static SettingsView from(AppSettings s) {
        return new SettingsView(s.getCurrency().getCurrencyCode(), s.getCurrency().getDisplayName(Locale.FRENCH),
                s.getCurrency().getSymbol(Locale.FRENCH), Math.max(0, s.getCurrency().getDefaultFractionDigits()),
                s.getUpdatedAt(), s.getUpdatedBy());
    }
}
