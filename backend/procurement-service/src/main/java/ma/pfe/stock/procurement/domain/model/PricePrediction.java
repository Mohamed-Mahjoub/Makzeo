package ma.pfe.stock.procurement.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Prédiction de l'évolution du prix d'un article.
 *
 * @param changePct       variation attendue en % (positive : hausse)
 * @param horizonDays     échéance de la prédiction
 * @param confidence      niveau de confiance entre 0 et 1
 * @param marketSignalPct tendance du marché relevée par l'agent IA (null si non disponible)
 */
public record PricePrediction(Long id, Long productId, String productReference, String productName,
                              Instant generatedAt, BigDecimal currentPrice, BigDecimal predictedPrice,
                              BigDecimal changePct, int horizonDays, double confidence,
                              ConfidenceLevel confidenceLevel, BigDecimal monthlyTrendPct, BigDecimal seasonalityPct,
                              BigDecimal inflationPct, BigDecimal marketSignalPct, int sampleSize, String rationale) {
}
