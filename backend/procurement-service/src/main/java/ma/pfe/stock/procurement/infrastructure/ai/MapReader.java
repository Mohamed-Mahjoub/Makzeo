package ma.pfe.stock.procurement.infrastructure.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Lecture tolérante des arguments JSON renvoyés par le modèle. */
final class MapReader {

    private MapReader() {
    }

    static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    static BigDecimal decimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String s = str(map, key);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(" ", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer integer(Map<String, Object> map, String key) {
        BigDecimal d = decimal(map, key);
        return d == null ? null : d.intValue();
    }

    static boolean bool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }

    static LocalDate date(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(Map.class::isInstance).map(o -> (Map<String, Object>) o).toList();
        }
        return List.of();
    }

    static List<String> strings(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(o -> o != null).map(Object::toString).toList();
        }
        return List.of();
    }

    /** URL http(s) uniquement. */
    static String url(Map<String, Object> map, String key) {
        String s = str(map, key);
        return s != null && (s.startsWith("https://") || s.startsWith("http://")) && s.length() <= 500 ? s : null;
    }
}
