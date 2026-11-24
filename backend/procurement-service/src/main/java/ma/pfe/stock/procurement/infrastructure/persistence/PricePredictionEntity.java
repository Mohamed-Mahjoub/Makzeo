package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.ConfidenceLevel;
import ma.pfe.stock.procurement.domain.model.PricePrediction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "price_predictions")
public class PricePredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long productId;
    @Column(nullable = false, length = 50)
    private String productReference;
    @Column(nullable = false, length = 150)
    private String productName;
    @Column(nullable = false)
    private Instant generatedAt;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal currentPrice;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal predictedPrice;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal changePct;
    @Column(nullable = false)
    private int horizonDays;
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal confidence;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConfidenceLevel confidenceLevel;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal monthlyTrendPct;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal seasonalityPct;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal inflationPct;
    @Column(precision = 7, scale = 2)
    private BigDecimal marketSignalPct;
    @Column(nullable = false)
    private int sampleSize;
    @Column(length = 2000)
    private String rationale;

    protected PricePredictionEntity() {
    }

    void copyFrom(PricePrediction p) {
        productId = p.productId();
        productReference = p.productReference();
        productName = SourcingLeadEntity.cut(p.productName(), 150);
        generatedAt = p.generatedAt() != null ? p.generatedAt() : Instant.now();
        currentPrice = p.currentPrice();
        predictedPrice = p.predictedPrice();
        changePct = p.changePct();
        horizonDays = p.horizonDays();
        confidence = BigDecimal.valueOf(p.confidence()).setScale(2, RoundingMode.HALF_UP);
        confidenceLevel = p.confidenceLevel();
        monthlyTrendPct = p.monthlyTrendPct();
        seasonalityPct = p.seasonalityPct();
        inflationPct = p.inflationPct();
        marketSignalPct = p.marketSignalPct();
        sampleSize = p.sampleSize();
        rationale = SourcingLeadEntity.cut(p.rationale(), 2000);
    }

    static PricePredictionEntity create() {
        return new PricePredictionEntity();
    }

    PricePrediction toDomain() {
        return new PricePrediction(id, productId, productReference, productName, generatedAt, currentPrice,
                predictedPrice, changePct, horizonDays, confidence.doubleValue(), confidenceLevel, monthlyTrendPct,
                seasonalityPct, inflationPct, marketSignalPct, sampleSize, rationale);
    }
}
