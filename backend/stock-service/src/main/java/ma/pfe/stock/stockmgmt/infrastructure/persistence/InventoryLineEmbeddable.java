package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

@Embeddable
public class InventoryLineEmbeddable {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_reference", nullable = false, length = 50)
    private String productReference;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    @Column(name = "counted_quantity", nullable = false)
    private int countedQuantity;

    @Column(name = "counted_by", length = 50)
    private String countedBy;

    @Column(name = "counted_at")
    private Instant countedAt;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getProductReference() { return productReference; }
    public void setProductReference(String productReference) { this.productReference = productReference; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public int getExpectedQuantity() { return expectedQuantity; }
    public void setExpectedQuantity(int expectedQuantity) { this.expectedQuantity = expectedQuantity; }
    public int getCountedQuantity() { return countedQuantity; }
    public void setCountedQuantity(int countedQuantity) { this.countedQuantity = countedQuantity; }
    public String getCountedBy() { return countedBy; }
    public void setCountedBy(String countedBy) { this.countedBy = countedBy; }
    public Instant getCountedAt() { return countedAt; }
    public void setCountedAt(Instant countedAt) { this.countedAt = countedAt; }
}
