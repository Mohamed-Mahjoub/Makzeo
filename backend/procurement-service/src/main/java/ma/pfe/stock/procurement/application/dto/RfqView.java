package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;

public record RfqView(Long id, String reference, Long productId, String productReference, String productName,
                      Long marketSupplierId, String supplierName, String supplierEmail, int quantity, String subject,
                      String body, RfqStatus status, LocalDate responseDueDate, String createdBy, Instant createdAt,
                      String approvedBy, Instant approvedAt, Instant sentAt, String failureReason) {

    public static RfqView from(Rfq r) {
        return new RfqView(r.getId(), r.getReference(), r.getProductId(), r.getProductReference(), r.getProductName(),
                r.getMarketSupplierId(), r.getSupplierName(), r.getSupplierEmail(), r.getQuantity(), r.getSubject(),
                r.getBody(), r.getStatus(), r.getResponseDueDate(), r.getCreatedBy(), r.getCreatedAt(),
                r.getApprovedBy(), r.getApprovedAt(), r.getSentAt(), r.getFailureReason());
    }
}
