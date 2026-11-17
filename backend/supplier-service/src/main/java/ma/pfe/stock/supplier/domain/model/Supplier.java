package ma.pfe.stock.supplier.domain.model;

import java.time.Instant;
import java.util.regex.Pattern;

/** Entité métier "Fournisseur". */
public class Supplier {

    /** Maroc : ICE (Identifiant Commun de l'Entreprise) - 15 chiffres. */
    private static final Pattern ICE = Pattern.compile("^\\d{15}$");
    /** Autres pays : matricule fiscal, n° TVA, SIRET, EIN... (3 à 30 caractères). */
    private static final Pattern TAX_ID = Pattern.compile("^[A-Za-z0-9 ./-]{3,30}$");

    private final Long id;
    private String code;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String taxId;
    private String notes;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Supplier(Long id, String code, String name, String contactName, String email, String phone,
                    String address, String city, String country, String taxId, String notes, boolean active,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.active = active;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        apply(code, name, contactName, email, phone, address, city, country, taxId, notes);
    }

    public static Supplier create(String code, String name, String contactName, String email, String phone,
                                  String address, String city, String country, String taxId, String notes) {
        return new Supplier(null, code, name, contactName, email, phone, address, city, country, taxId, notes,
                true, Instant.now(), null);
    }

    public void update(String code, String name, String contactName, String email, String phone, String address,
                       String city, String country, String taxId, String notes) {
        apply(code, name, contactName, email, phone, address, city, country, taxId, notes);
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    private void apply(String code, String name, String contactName, String email, String phone, String address,
                       String city, String country, String taxId, String notes) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code fournisseur est obligatoire");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("La raison sociale est obligatoire");
        }
        String normalizedCountry = blankToNull(country) == null ? null : country.trim().toUpperCase();
        if (normalizedCountry != null && !Countries.isValid(normalizedCountry)) {
            throw new IllegalArgumentException("Code pays ISO 3166 inconnu : " + country);
        }
        String normalizedTaxId = blankToNull(taxId) == null ? null : taxId.trim().toUpperCase();
        if (normalizedTaxId != null) {
            if ("MA".equals(normalizedCountry) && !ICE.matcher(normalizedTaxId).matches()) {
                throw new IllegalArgumentException("Pour le Maroc, l'identifiant fiscal (ICE) doit contenir 15 chiffres");
            }
            if (!TAX_ID.matcher(normalizedTaxId).matches()) {
                throw new IllegalArgumentException(
                        "Identifiant fiscal invalide : 3 à 30 caractères (lettres, chiffres, espace . / -)");
            }
        }
        this.code = code.trim().toUpperCase();
        this.name = name.trim();
        this.contactName = blankToNull(contactName);
        this.email = blankToNull(email) == null ? null : email.trim().toLowerCase();
        this.phone = blankToNull(phone);
        this.address = blankToNull(address);
        this.city = blankToNull(city);
        this.country = normalizedCountry;
        this.taxId = normalizedTaxId;
        this.notes = blankToNull(notes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getContactName() { return contactName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getTaxId() { return taxId; }
    public String getCountryName() { return Countries.nameOf(country); }
    public String getNotes() { return notes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
