package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "purchase_recommendations")
public class PurchaseRecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecommendationType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false, length = 50)
    private String productReference;
    @Column(nullable = false, length = 150)
    private String productName;
    @Column(length = 150)
    private String supplierName;
    private Long quoteId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal referenceUnitPrice;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal recommendedUnitPrice;
    @Column(precision = 14, scale = 3)
    private BigDecimal expectedFuturePrice;
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal costNow;
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal costLater;
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal holdingCost;
    @Column(nullable = false, precision = 16, scale = 3)
    private BigDecimal netSavings;
    private LocalDate buyBefore;
    @Column(precision = 4, scale = 2)
    private BigDecimal confidence;
    @Column(length = 2000)
    private String rationale;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 50)
    private String decidedBy;
    private Instant decidedAt;
    private Instant notifiedAt;

    protected PurchaseRecommendationEntity() {
    }

    static PurchaseRecommendationEntity from(PurchaseRecommendation r) {
        PurchaseRecommendationEntity e = new PurchaseRecommendationEntity();
        e.id = r.getId();
        e.type = r.getType();
        e.status = r.getStatus();
        e.productId = r.getProductId();
        e.productReference = r.getProductReference();
        e.productName = SourcingLeadEntity.cut(r.getProductName(), 150);
        e.supplierName = SourcingLeadEntity.cut(r.getSupplierName(), 150);
        e.quoteId = r.getQuoteId();
        e.quantity = r.getQuantity();
        e.referenceUnitPrice = r.getReferenceUnitPrice();
        e.recommendedUnitPrice = r.getRecommendedUnitPrice();
        e.expectedFuturePrice = r.getExpectedFuturePrice();
        e.costNow = r.getCostNow();
        e.costLater = r.getCostLater();
        e.holdingCost = r.getHoldingCost();
        e.netSavings = r.getNetSavings();
        e.buyBefore = r.getBuyBefore();
        e.confidence = r.getConfidence() == null ? null
                : BigDecimal.valueOf(r.getConfidence()).setScale(2, RoundingMode.HALF_UP);
        e.rationale = SourcingLeadEntity.cut(r.getRationale(), 2000);
        e.createdAt = r.getCreatedAt();
        e.decidedBy = r.getDecidedBy();
        e.decidedAt = r.getDecidedAt();
        e.notifiedAt = r.getNotifiedAt();
        return e;
    }

    PurchaseRecommendation toDomain() {
        return new PurchaseRecommendation(id, type, status, productId, productReference, productName, supplierName,
                quoteId, quantity, referenceUnitPrice, recommendedUnitPrice, expectedFuturePrice, costNow, costLater,
                holdingCost, netSavings, buyBefore, confidence == null ? null : confidence.doubleValue(), rationale,
                createdAt, decidedBy, decidedAt, notifiedAt);
    }
}
