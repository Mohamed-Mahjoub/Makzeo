package ma.pfe.stock.product.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Entité métier "Produit". Toutes les règles d'intégrité du produit sont portées ici.
 */
public class Product {

    private final Long id;
    private String reference;
    private String name;
    private String description;
    private Long categoryId;
    private Barcode barcode;
    private UnitOfMeasure unit;
    private BigDecimal purchasePrice;
    private BigDecimal salePrice;
    private int minStock;
    private Integer maxStock;
    private Long supplierId;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    public Product(Long id, String reference, String name, String description, Long categoryId, Barcode barcode,
                   UnitOfMeasure unit, BigDecimal purchasePrice, BigDecimal salePrice, int minStock,
                   Integer maxStock, Long supplierId, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.reference = normalizeReference(reference);
        this.barcode = Objects.requireNonNull(barcode, "barcode");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
        this.active = active;
        applyDetails(name, description, categoryId, unit, purchasePrice, salePrice, minStock, maxStock, supplierId);
    }

    public static Product create(String reference, String name, String description, Long categoryId, Barcode barcode,
                                 UnitOfMeasure unit, BigDecimal purchasePrice, BigDecimal salePrice, int minStock,
                                 Integer maxStock, Long supplierId) {
        return new Product(null, reference, name, description, categoryId, barcode, unit, purchasePrice,
                salePrice, minStock, maxStock, supplierId, true, Instant.now(), null);
    }

    public void update(String reference, String name, String description, Long categoryId, Barcode barcode,
                       UnitOfMeasure unit, BigDecimal purchasePrice, BigDecimal salePrice, int minStock,
                       Integer maxStock, Long supplierId) {
        this.reference = normalizeReference(reference);
        this.barcode = Objects.requireNonNull(barcode, "barcode");
        applyDetails(name, description, categoryId, unit, purchasePrice, salePrice, minStock, maxStock, supplierId);
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

    private void applyDetails(String name, String description, Long categoryId, UnitOfMeasure unit,
                              BigDecimal purchasePrice, BigDecimal salePrice, int minStock, Integer maxStock,
                              Long supplierId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du produit est obligatoire");
        }
        if (categoryId == null) {
            throw new IllegalArgumentException("La catégorie est obligatoire");
        }
        if (minStock < 0) {
            throw new BusinessRuleException("INVALID_MIN_STOCK", "Le seuil minimum doit être positif ou nul");
        }
        if (maxStock != null && maxStock < minStock) {
            throw new BusinessRuleException("INVALID_MAX_STOCK",
                    "Le stock maximum doit être supérieur ou égal au seuil minimum");
        }
        requirePositiveOrZero(purchasePrice, "prix d'achat");
        requirePositiveOrZero(salePrice, "prix de vente");
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.categoryId = categoryId;
        this.unit = unit != null ? unit : UnitOfMeasure.PIECE;
        this.purchasePrice = purchasePrice != null ? purchasePrice : BigDecimal.ZERO;
        this.salePrice = salePrice != null ? salePrice : BigDecimal.ZERO;
        this.minStock = minStock;
        this.maxStock = maxStock;
        this.supplierId = supplierId;
    }

    private static void requirePositiveOrZero(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            throw new BusinessRuleException("INVALID_PRICE", "Le " + label + " doit être positif ou nul");
        }
    }

    private static String normalizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("La référence est obligatoire");
        }
        return reference.trim().toUpperCase();
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Long getCategoryId() { return categoryId; }
    public Barcode getBarcode() { return barcode; }
    public UnitOfMeasure getUnit() { return unit; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public BigDecimal getSalePrice() { return salePrice; }
    public int getMinStock() { return minStock; }
    public Integer getMaxStock() { return maxStock; }
    public Long getSupplierId() { return supplierId; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
