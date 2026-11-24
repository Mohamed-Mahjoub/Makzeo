package ma.pfe.stock.procurement.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Données du rapport de synthèse de l'agent IA achats (indépendantes du format de rendu).
 *
 * @param generatedAt date de génération
 * @param generatedBy utilisateur ayant demandé le rapport
 * @param currency    devise des montants (HT)
 */
public record AiReport(Instant generatedAt, String generatedBy, String currency, String marketName,
                       boolean aiEnabled, Kpis kpis, List<RecommendationLine> recommendations,
                       List<PredictionLine> predictions, List<ComparisonLine> comparisons,
                       List<AmountLine> savingsBySupplier, List<AmountLine> savingsByMonth, WatchRunView lastRun) {

    /** Indicateurs clés. */
    public record Kpis(long openOpportunities, long acceptedOpportunities, long dismissedOpportunities,
                       BigDecimal potentialSavings, BigDecimal realizedSavings, BigDecimal supplierSwitchGains,
                       BigDecimal anticipatedPurchaseGains, long trackedProducts, long risingPrices,
                       long fallingPrices, long highConfidencePredictions, long comparedProducts,
                       long candidateSuppliers, long validatedSuppliers, long draftRfqs, long sentRfqs) {
    }

    /** Recommandation d'achat (changement de fournisseur ou achat anticipé). */
    public record RecommendationLine(String type, String status, String productReference, String productName,
                                     String supplierName, int quantity, BigDecimal referencePrice,
                                     BigDecimal recommendedPrice, BigDecimal netSavings, LocalDate buyBefore,
                                     Instant createdAt) {
    }

    /** Prédiction de prix. */
    public record PredictionLine(String productReference, String productName, BigDecimal currentPrice,
                                 BigDecimal predictedPrice, BigDecimal changePct, int horizonDays, double confidence,
                                 String confidenceLevel, int sampleSize) {
    }

    /** Meilleure offre d'un article comparée au dernier prix payé. */
    public record ComparisonLine(String productReference, String productName, int offers, String bestSupplier,
                                 String bestOffer, boolean equivalent, BigDecimal bestPrice,
                                 BigDecimal referencePrice, BigDecimal savingPct, BigDecimal annualSaving) {
    }

    /** Montant agrégé (fournisseur, mois). */
    public record AmountLine(String label, BigDecimal realized, BigDecimal potential, int count) {
    }
}
