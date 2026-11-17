package ma.pfe.stock.stockmgmt.application.dto;

import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.StockAlert;

import java.time.Instant;

public record AlertView(Long id, Long productId, String productReference, String productName, AlertType type,
                        String typeLabel, AlertStatus status, int quantity, int threshold, String message,
                        Instant createdAt, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt) {

    public static AlertView from(StockAlert a) {
        return new AlertView(a.getId(), a.getProductId(), a.getProductReference(), a.getProductName(), a.getType(),
                a.getType().label(), a.getStatus(), a.getQuantity(), a.getThreshold(), a.message(),
                a.getCreatedAt(), a.getAcknowledgedBy(), a.getAcknowledgedAt(), a.getResolvedAt());
    }
}
