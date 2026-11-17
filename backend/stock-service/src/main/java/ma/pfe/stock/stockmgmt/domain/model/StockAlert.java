package ma.pfe.stock.stockmgmt.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.time.Instant;

/** Alerte de stock (rupture ou seuil minimum). */
public class StockAlert {

    private final Long id;
    private final Long productId;
    private final String productReference;
    private final String productName;
    private final AlertType type;
    private AlertStatus status;
    private int quantity;
    private final int threshold;
    private final Instant createdAt;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant resolvedAt;

    public StockAlert(Long id, Long productId, String productReference, String productName, AlertType type,
                      AlertStatus status, int quantity, int threshold, Instant createdAt, String acknowledgedBy,
                      Instant acknowledgedAt, Instant resolvedAt) {
        this.id = id;
        this.productId = productId;
        this.productReference = productReference;
        this.productName = productName;
        this.type = type;
        this.status = status;
        this.quantity = quantity;
        this.threshold = threshold;
        this.createdAt = createdAt;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
        this.resolvedAt = resolvedAt;
    }

    public static StockAlert raise(StockItem item, AlertType type) {
        return new StockAlert(null, item.getProductId(), item.getProductReference(), item.getProductName(), type,
                AlertStatus.OPEN, item.getQuantity(), item.getMinStock(), Instant.now(), null, null, null);
    }

    public boolean isActive() {
        return status != AlertStatus.RESOLVED;
    }

    public void updateQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void acknowledge(String username) {
        if (status != AlertStatus.OPEN) {
            throw new BusinessRuleException("ALERT_NOT_OPEN", "Seule une alerte ouverte peut être acquittée");
        }
        this.status = AlertStatus.ACKNOWLEDGED;
        this.acknowledgedBy = username;
        this.acknowledgedAt = Instant.now();
    }

    public void resolve() {
        this.status = AlertStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }

    public String message() {
        return type.label() + " : " + productReference + " - " + productName + " (quantité " + quantity
                + ", seuil " + threshold + ")";
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public AlertType getType() { return type; }
    public AlertStatus getStatus() { return status; }
    public int getQuantity() { return quantity; }
    public int getThreshold() { return threshold; }
    public Instant getCreatedAt() { return createdAt; }
    public String getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
