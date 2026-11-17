package ma.pfe.stock.reporting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Devise de l'application (code ISO 4217 et nombre de décimales). */
public record CurrencyInfo(String code, int fractionDigits) {

    public static final CurrencyInfo DEFAULT = new CurrencyInfo("MAD", 2);

    public BigDecimal round(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(fractionDigits, RoundingMode.HALF_UP);
    }
}
