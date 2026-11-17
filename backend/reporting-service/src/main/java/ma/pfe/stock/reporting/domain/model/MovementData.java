package ma.pfe.stock.reporting.domain.model;

import java.time.Instant;

public record MovementData(Long id, Long productId, String productReference, String productName, String type, String typeLabel,
                           int quantity, int signedQuantity, int quantityBefore, int quantityAfter,
                           String documentReference, String reason, String performedBy, Instant occurredAt) {
}
