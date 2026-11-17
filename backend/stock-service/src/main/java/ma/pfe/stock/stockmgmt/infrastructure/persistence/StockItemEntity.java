package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "stock_items")
public class StockItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "product_reference", nullable = false, length = 50)
    private String productReference;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    @Version
    private Long version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductReference() { return productReference; }
    public void setProductReference(String productReference) { this.productReference = productReference; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getMinStock() { return minStock; }
    public void setMinStock(int minStock) { this.minStock = minStock; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getLastMovementAt() { return lastMovementAt; }
    public void setLastMovementAt(Instant lastMovementAt) { this.lastMovementAt = lastMovementAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
