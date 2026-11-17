package ma.pfe.stock.stockmgmt.domain.model;

import java.time.Instant;

/** Ligne de comptage d'un inventaire. */
public record InventoryLine(
        Long productId,
        String productReference,
        String productName,
        int expectedQuantity,
        int countedQuantity,
        String countedBy,
        Instant countedAt) {

    public InventoryLine {
        if (countedQuantity < 0) {
            throw new IllegalArgumentException("La quantité comptée ne peut pas être négative");
        }
    }

    public int difference() {
        return countedQuantity - expectedQuantity;
    }
}
