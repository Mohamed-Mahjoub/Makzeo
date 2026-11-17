package ma.pfe.stock.stockmgmt.application.dto;

import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.StockMovement;

import java.time.Instant;

public record MovementView(Long id, Long productId, String productReference, String productName, MovementType type,
                           String typeLabel, int quantity, int signedQuantity, int quantityBefore,
                           int quantityAfter, Long supplierId, String documentReference, String reason,
                           Long inventoryId, String performedBy, Instant occurredAt) {

    public static MovementView from(StockMovement m) {
        return new MovementView(m.id(), m.productId(), m.productReference(), m.productName(), m.type(),
                m.type().label(), m.quantity(), m.signedQuantity(), m.quantityBefore(), m.quantityAfter(),
                m.supplierId(), m.documentReference(), m.reason(), m.inventoryId(), m.performedBy(),
                m.occurredAt());
    }
}
