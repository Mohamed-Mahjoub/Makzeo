package ma.pfe.stock.supplier.domain.model;

import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Référentiel des pays (norme ISO 3166-1 alpha-2, libellés en français), fourni par le JDK. */
public final class Countries {

    private static final Set<String> CODES = Set.of(Locale.getISOCountries());

    private Countries() {
    }

    public record Country(String code, String name) {
    }

    public static boolean isValid(String code) {
        return code != null && CODES.contains(code.toUpperCase(Locale.ROOT));
    }

    public static String nameOf(String code) {
        return code == null ? null : Locale.of("", code).getDisplayCountry(Locale.FRENCH);
    }

    /** Tous les pays, triés par nom français. */
    public static List<Country> all() {
        Collator collator = Collator.getInstance(Locale.FRENCH);
        return Arrays.stream(Locale.getISOCountries())
                .map(code -> new Country(code, nameOf(code)))
                .sorted(Comparator.comparing(Country::name, collator))
                .collect(Collectors.toList());
    }
}
