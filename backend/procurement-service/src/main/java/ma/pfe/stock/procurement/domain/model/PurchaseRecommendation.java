package ma.pfe.stock.procurement.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Recommandation d'achat chiffrée.
 *
 * @param referenceUnitPrice   dernier prix payé (base de comparaison)
 * @param recommendedUnitPrice prix de l'offre recommandée
 * @param expectedFuturePrice  prix attendu après la hausse (achat anticipé)
 * @param costNow              coût de l'achat recommandé
 * @param costLater            coût de la même quantité sans suivre la recommandation
 * @param holdingCost          coût de stockage induit (achat anticipé)
 * @param netSavings           économie nette = costLater - costNow - holdingCost
 */
public class PurchaseRecommendation {

    private final Long id;
    private final RecommendationType type;
    private RecommendationStatus status;
    private final Long productId;
    private final String productReference;
    private final String productName;
    private final String supplierName;
    private final Long quoteId;
    private final int quantity;
    private final BigDecimal referenceUnitPrice;
    private final BigDecimal recommendedUnitPrice;
    private final BigDecimal expectedFuturePrice;
    private final BigDecimal costNow;
    private final BigDecimal costLater;
    private final BigDecimal holdingCost;
    private final BigDecimal netSavings;
    private final LocalDate buyBefore;
    private final Double confidence;
    private final String rationale;
    private final Instant createdAt;
    private String decidedBy;
    private Instant decidedAt;
    private Instant notifiedAt;

    public PurchaseRecommendation(Long id, RecommendationType type, RecommendationStatus status, Long productId,
                                  String productReference, String productName, String supplierName, Long quoteId,
                                  int quantity, BigDecimal referenceUnitPrice, BigDecimal recommendedUnitPrice,
                                  BigDecimal expectedFuturePrice, BigDecimal costNow, BigDecimal costLater,
                                  BigDecimal holdingCost, BigDecimal netSavings, LocalDate buyBefore,
                                  Double confidence, String rationale, Instant createdAt, String decidedBy,
                                  Instant decidedAt, Instant notifiedAt) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité recommandée doit être positive");
        }
        this.id = id;
        this.type = type;
        this.status = status;
        this.productId = productId;
        this.productReference = productReference;
        this.productName = productName;
        this.supplierName = supplierName;
        this.quoteId = quoteId;
        this.quantity = quantity;
        this.referenceUnitPrice = referenceUnitPrice;
        this.recommendedUnitPrice = recommendedUnitPrice;
        this.expectedFuturePrice = expectedFuturePrice;
        this.costNow = costNow;
        this.costLater = costLater;
        this.holdingCost = holdingCost;
        this.netSavings = netSavings;
        this.buyBefore = buyBefore;
        this.confidence = confidence;
        this.rationale = rationale;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
        this.notifiedAt = notifiedAt;
    }

    public void accept(String user) {
        decide(RecommendationStatus.ACCEPTED, user);
    }

    public void dismiss(String user) {
        decide(RecommendationStatus.DISMISSED, user);
    }

    /** Remplacée par une recommandation plus récente pour le même article. */
    public void expire() {
        if (status == RecommendationStatus.OPEN) {
            this.status = RecommendationStatus.EXPIRED;
            this.decidedAt = Instant.now();
        }
    }

    public void markNotified() {
        this.notifiedAt = Instant.now();
    }

    private void decide(RecommendationStatus target, String user) {
        if (status != RecommendationStatus.OPEN) {
            throw new BusinessRuleException("RECOMMENDATION_CLOSED", "Cette recommandation a déjà été traitée");
        }
        this.status = target;
        this.decidedBy = user;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public RecommendationType getType() { return type; }
    public RecommendationStatus getStatus() { return status; }
    public Long getProductId() { return productId; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public String getSupplierName() { return supplierName; }
    public Long getQuoteId() { return quoteId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getReferenceUnitPrice() { return referenceUnitPrice; }
    public BigDecimal getRecommendedUnitPrice() { return recommendedUnitPrice; }
    public BigDecimal getExpectedFuturePrice() { return expectedFuturePrice; }
    public BigDecimal getCostNow() { return costNow; }
    public BigDecimal getCostLater() { return costLater; }
    public BigDecimal getHoldingCost() { return holdingCost; }
    public BigDecimal getNetSavings() { return netSavings; }
    public LocalDate getBuyBefore() { return buyBefore; }
    public Double getConfidence() { return confidence; }
    public String getRationale() { return rationale; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getNotifiedAt() { return notifiedAt; }
}
