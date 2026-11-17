package ma.pfe.stock.auth.application.service;

import ma.pfe.stock.auth.application.dto.CurrencyView;
import ma.pfe.stock.auth.application.dto.SettingsView;
import ma.pfe.stock.auth.domain.model.AppSettings;
import ma.pfe.stock.auth.domain.port.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Cas d'utilisation : lecture et modification des paramètres de l'application (devise). */
@Service
public class SettingsService {

    /** Devises mises en avant dans la liste (Maghreb, Europe, Amérique du Nord, Golfe, Afrique). */
    static final List<String> COMMON = List.of("MAD", "TND", "DZD", "EUR", "USD", "GBP", "CHF", "CAD", "XOF",
            "XAF", "EGP", "LYD", "MRU", "SAR", "AED", "QAR", "TRY", "CNY");

    private final SettingsRepository repository;

    public SettingsService(SettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SettingsView get() {
        return SettingsView.from(repository.load().orElseGet(AppSettings::defaults));
    }

    @Transactional
    public SettingsView changeCurrency(String currencyCode, String username) {
        AppSettings settings = repository.load().orElseGet(AppSettings::defaults);
        settings.changeCurrency(currencyCode, username);
        return SettingsView.from(repository.save(settings));
    }

    /**
     * Devises ayant cours aujourd'hui : celles utilisées par au moins un pays (norme ISO 3166 / ISO 4217),
     * les plus courantes en premier, puis les autres par ordre alphabétique de leur nom français.
     */
    public List<CurrencyView> availableCurrencies() {
        Set<String> codes = new TreeSet<>(COMMON);
        for (String country : Locale.getISOCountries()) {
            try {
                Currency currency = Currency.getInstance(Locale.of("", country));
                if (currency != null) {
                    codes.add(currency.getCurrencyCode());
                }
            } catch (IllegalArgumentException ignored) {
                // pays sans devise propre (ex. Antarctique)
            }
        }
        Collator collator = Collator.getInstance(Locale.FRENCH);
        return codes.stream()
                .map(code -> {
                    Currency c = Currency.getInstance(code);
                    return new CurrencyView(code, capitalize(c.getDisplayName(Locale.FRENCH)),
                            c.getSymbol(Locale.FRENCH), Math.max(0, c.getDefaultFractionDigits()),
                            COMMON.contains(code));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((CurrencyView v) -> !v.common())
                        .thenComparing(v -> v.common() ? String.format("%03d", COMMON.indexOf(v.code())) : "")
                        .thenComparing(CurrencyView::name, collator))
                .toList();
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
