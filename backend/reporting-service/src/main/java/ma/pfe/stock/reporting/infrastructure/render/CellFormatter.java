package ma.pfe.stock.reporting.infrastructure.render;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatage textuel des cellules (utilisé par le rendu PDF). */
final class CellFormatter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zoneId;

    CellFormatter(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    /** Montants formatés avec le nombre de décimales de la devise (2 pour l'euro, 3 pour le dinar tunisien...). */
    String format(Object value, int decimals) {
        if (value == null) {
            return "";
        }
        if (value instanceof Instant instant) {
            return DATE_TIME.format(instant.atZone(zoneId));
        }
        if (value instanceof BigDecimal decimal) {
            DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
            symbols.setGroupingSeparator(' ');
            String pattern = decimals > 0 ? "#,##0." + "0".repeat(decimals) : "#,##0";
            return new DecimalFormat(pattern, symbols).format(decimal.setScale(decimals, RoundingMode.HALF_UP));
        }
        return String.valueOf(value);
    }
}
