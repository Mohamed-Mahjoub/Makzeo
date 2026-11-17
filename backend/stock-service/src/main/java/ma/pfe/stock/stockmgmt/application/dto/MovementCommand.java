package ma.pfe.stock.stockmgmt.application.dto;

import ma.pfe.stock.stockmgmt.domain.model.MovementType;

/** Demande d'entrée (IN) ou de sortie (OUT) de stock. */
public record MovementCommand(Long productId, MovementType type, int quantity, Long supplierId,
                              String documentReference, String reason) {
}
