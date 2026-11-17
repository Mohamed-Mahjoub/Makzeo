package ma.pfe.stock.stockmgmt.domain.model;

import java.time.Instant;

/**
 * Mouvement de stock (immuable) : constitue l'historique / la traçabilité.
 * {@code quantity} est toujours positive ; le sens est donné par quantityBefore / quantityAfter.
 */
public record StockMovement(
        Long id,
        Long productId,
        String productReference,
        String productName,
        MovementType type,
        int quantity,
        int quantityBefore,
        int quantityAfter,
        Long supplierId,
        String documentReference,
        String reason,
        Long inventoryId,
        String performedBy,
        Instant occurredAt) {

    public StockMovement {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité d'un mouvement doit être strictement positive");
        }
        if (type == null) {
            throw new IllegalArgumentException("Le type de mouvement est obligatoire");
        }
    }

    public int signedQuantity() {
        return quantityAfter - quantityBefore;
    }
}
