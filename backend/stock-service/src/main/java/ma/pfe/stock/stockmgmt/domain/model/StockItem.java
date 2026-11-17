package ma.pfe.stock.stockmgmt.domain.model;

import java.time.Instant;

/**
 * Niveau de stock d'un produit. Les informations produit (référence, nom, seuil) sont une copie
 * locale rafraîchie à chaque mouvement : le service reste consultable même si product-service est indisponible.
 */
public class StockItem {

    private final Long id;
    private final Long productId;
    private String productReference;
    private String productName;
    private int minStock;
    private int quantity;
    private Instant lastMovementAt;

    public StockItem(Long id, Long productId, String productReference, String productName, int minStock,
                     int quantity, Instant lastMovementAt) {
        if (productId == null) {
            throw new IllegalArgumentException("productId obligatoire");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("La quantité en stock ne peut pas être négative");
        }
        this.id = id;
        this.productId = productId;
        this.productReference = productReference;
        this.productName = productName;
        this.minStock = minStock;
        this.quantity = quantity;
        this.lastMovementAt = lastMovementAt;
    }

    public static StockItem empty(ProductSnapshot product) {
        return new StockItem(null, product.id(), product.reference(), product.name(), product.minStock(), 0, null);
    }

    public void refreshProduct(ProductSnapshot product) {
        this.productReference = product.reference();
        this.productName = product.name();
        this.minStock = product.minStock();
    }

    /** Applique une entrée ; retourne la quantité avant mouvement. */
    public int increase(int qty) {
        requirePositive(qty);
        int before = quantity;
        quantity = Math.addExact(quantity, qty);
        lastMovementAt = Instant.now();
        return before;
    }

    /** Applique une sortie ; retourne la quantité avant mouvement. */
    public int decrease(int qty) {
        requirePositive(qty);
        if (qty > quantity) {
            throw new InsufficientStockException(productReference, quantity, qty);
        }
        int before = quantity;
        quantity -= qty;
        lastMovementAt = Instant.now();
        return before;
    }

    /** Fixe la quantité (inventaire) ; retourne la quantité avant ajustement. */
    public int adjustTo(int newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("La quantité inventoriée ne peut pas être négative");
        }
        int before = quantity;
        quantity = newQuantity;
        lastMovementAt = Instant.now();
        return before;
    }

    public StockStatus status() {
        return StockStatus.of(quantity, minStock);
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive");
        }
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public int getMinStock() { return minStock; }
    public int getQuantity() { return quantity; }
    public Instant getLastMovementAt() { return lastMovementAt; }
}
