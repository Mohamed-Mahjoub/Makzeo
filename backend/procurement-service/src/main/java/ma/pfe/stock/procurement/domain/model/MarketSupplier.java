package ma.pfe.stock.procurement.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Fournisseur du marché (catalogue, veille ou saisie manuelle). */
public class MarketSupplier {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> LEGAL_FORMS = Set.of("sarl", "suarl", "sa", "ste", "societe", "sas", "ets",
            "etablissements", "group", "groupe", "tunisie", "tunisia");

    private final Long id;
    private String name;
    private final String normalizedKey;
    private String website;
    private String email;
    private String phone;
    private String city;
    private final SupplierSource source;
    private MarketSupplierStatus status;
    private Long catalogSupplierId;
    private String notes;
    private final Instant discoveredAt;
    private Instant updatedAt;

    public MarketSupplier(Long id, String name, String website, String email, String phone, String city,
                          SupplierSource source, MarketSupplierStatus status, Long catalogSupplierId, String notes,
                          Instant discoveredAt, Instant updatedAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du fournisseur est obligatoire");
        }
        this.id = id;
        this.name = name.trim();
        this.normalizedKey = normalizeKey(name);
        this.website = cleanUrl(website);
        this.email = cleanEmail(email);
        this.phone = blankToNull(phone);
        this.city = blankToNull(city);
        this.source = source;
        this.status = status;
        this.catalogSupplierId = catalogSupplierId;
        this.notes = blankToNull(notes);
        this.discoveredAt = discoveredAt != null ? discoveredAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.discoveredAt;
    }

    /** Fournisseur détecté par la veille : à valider par un acheteur avant toute demande de prix. */
    public static MarketSupplier discovered(String name, String website, String email, String phone, String city) {
        return new MarketSupplier(null, name, website, email, phone, city, SupplierSource.WEB_WATCH,
                MarketSupplierStatus.CANDIDATE, null, null, Instant.now(), null);
    }

    /** Fournisseur déjà référencé dans le catalogue : validé d'office. */
    public static MarketSupplier fromCatalog(Long catalogSupplierId, String name, String email, String phone,
                                             String city) {
        return new MarketSupplier(null, name, null, email, phone, city, SupplierSource.CATALOG,
                MarketSupplierStatus.VALIDATED, catalogSupplierId, null, Instant.now(), null);
    }

    public static MarketSupplier manual(String name, String website, String email, String phone, String city,
                                        String notes) {
        return new MarketSupplier(null, name, website, email, phone, city, SupplierSource.MANUAL,
                MarketSupplierStatus.VALIDATED, null, notes, Instant.now(), null);
    }

    /**
     * Clé de rapprochement : minuscules, sans accents ni ponctuation, sans forme juridique.
     * "Sté Mytek SARL" et "MYTEK" donnent la même clé.
     */
    public static String normalizeKey(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace(".", "")
                .replaceAll("[^a-z0-9 ]", " ");
        StringBuilder key = new StringBuilder();
        for (String word : ascii.trim().split("\\s+")) {
            if (!word.isEmpty() && !LEGAL_FORMS.contains(word)) {
                key.append(word);
            }
        }
        String result = key.isEmpty() ? ascii.replace(" ", "") : key.toString();
        return result.length() > 150 ? result.substring(0, 150) : result;
    }

    /** Complète les coordonnées manquantes sans écraser celles déjà connues. */
    public void completeContact(String website, String email, String phone, String city) {
        if (this.website == null) {
            this.website = cleanUrl(website);
        }
        if (this.email == null) {
            this.email = cleanEmail(email);
        }
        if (this.phone == null) {
            this.phone = blankToNull(phone);
        }
        if (this.city == null) {
            this.city = blankToNull(city);
        }
        this.updatedAt = Instant.now();
    }

    public void updateDetails(String name, String website, String email, String phone, String city, String notes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du fournisseur est obligatoire");
        }
        if (email != null && !email.isBlank() && cleanEmail(email) == null) {
            throw new IllegalArgumentException("Adresse e-mail invalide : " + email);
        }
        this.name = name.trim();
        this.website = cleanUrl(website);
        this.email = cleanEmail(email);
        this.phone = blankToNull(phone);
        this.city = blankToNull(city);
        this.notes = blankToNull(notes);
        this.updatedAt = Instant.now();
    }

    public void validate() {
        this.status = MarketSupplierStatus.VALIDATED;
        this.updatedAt = Instant.now();
    }

    public void reject() {
        if (source == SupplierSource.CATALOG) {
            throw new BusinessRuleException("SUPPLIER_FROM_CATALOG",
                    "Un fournisseur du catalogue se désactive dans le module Fournisseurs");
        }
        this.status = MarketSupplierStatus.REJECTED;
        this.updatedAt = Instant.now();
    }

    public void linkToCatalog(Long catalogSupplierId) {
        this.catalogSupplierId = catalogSupplierId;
        this.updatedAt = Instant.now();
    }

    /** Une demande de prix ne part que vers un fournisseur validé disposant d'une adresse e-mail. */
    public boolean canReceiveRfq() {
        return status == MarketSupplierStatus.VALIDATED && email != null;
    }

    static String cleanEmail(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        v = v.toLowerCase(Locale.ROOT);
        return EMAIL.matcher(v).matches() && v.length() <= 150 ? v : null;
    }

    static String cleanUrl(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "https://" + v;
        }
        return v.length() <= 255 ? v : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getNormalizedKey() { return normalizedKey; }
    public String getWebsite() { return website; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCity() { return city; }
    public SupplierSource getSource() { return source; }
    public MarketSupplierStatus getStatus() { return status; }
    public Long getCatalogSupplierId() { return catalogSupplierId; }
    public String getNotes() { return notes; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
