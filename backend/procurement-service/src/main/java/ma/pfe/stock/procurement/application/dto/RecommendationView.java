package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecommendationView(Long id, RecommendationType type, RecommendationStatus status, Long productId,
                                 String productReference, String productName, String supplierName, Long quoteId,
                                 int quantity, BigDecimal referenceUnitPrice, BigDecimal recommendedUnitPrice,
                                 BigDecimal expectedFuturePrice, BigDecimal costNow, BigDecimal costLater,
                                 BigDecimal holdingCost, BigDecimal netSavings, LocalDate buyBefore,
                                 Double confidence, String rationale, Instant createdAt, String decidedBy,
                                 Instant decidedAt) {

    public static RecommendationView from(PurchaseRecommendation r) {
        return new RecommendationView(r.getId(), r.getType(), r.getStatus(), r.getProductId(), r.getProductReference(),
                r.getProductName(), r.getSupplierName(), r.getQuoteId(), r.getQuantity(), r.getReferenceUnitPrice(),
                r.getRecommendedUnitPrice(), r.getExpectedFuturePrice(), r.getCostNow(), r.getCostLater(),
                r.getHoldingCost(), r.getNetSavings(), r.getBuyBefore(), r.getConfidence(), r.getRationale(),
                r.getCreatedAt(), r.getDecidedBy(), r.getDecidedAt());
    }
}
