package ma.pfe.stock.procurement.application.service;

import ma.pfe.stock.procurement.application.dto.AiReport;
import ma.pfe.stock.procurement.application.dto.ComparisonView;
import ma.pfe.stock.procurement.application.dto.OverviewView;
import ma.pfe.stock.procurement.application.port.AiReportRenderer;
import ma.pfe.stock.procurement.domain.model.ConfidenceLevel;
import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Assemble le rapport de synthèse de l'agent IA (apport du module) puis le fait rendre en PDF. */
@Service
public class AiReportService {

    static final int MAX_LINES = 25;

    private final OverviewService overview;
    private final RecommendationService recommendations;
    private final PriceService prices;
    private final QuoteService quotes;
    private final AiReportRenderer renderer;

    public AiReportService(OverviewService overview, RecommendationService recommendations, PriceService prices,
                           QuoteService quotes, AiReportRenderer renderer) {
        this.overview = overview;
        this.recommendations = recommendations;
        this.prices = prices;
        this.quotes = quotes;
        this.renderer = renderer;
    }

    public byte[] pdf(String user) {
        return renderer.render(build(user));
    }

    public AiReport build(String user) {
        OverviewView ov = overview.overview();
        SavingsCalculator.Summary savings = overview.savings();
        List<PurchaseRecommendation> recos = recommendations.all();
        List<PricePrediction> predictions = prices.predictions();

        List<AiReport.RecommendationLine> recoLines = recos.stream()
                .filter(r -> r.getStatus() != RecommendationStatus.EXPIRED)
                .sorted(Comparator.comparing(PurchaseRecommendation::getNetSavings,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_LINES)
                .map(r -> new AiReport.RecommendationLine(
                        r.getType() == RecommendationType.SWITCH_SUPPLIER ? "Changement de fournisseur" : "Achat anticipé",
                        statusLabel(r.getStatus()), r.getProductReference(), r.getProductName(), r.getSupplierName(),
                        r.getQuantity(), r.getReferenceUnitPrice(), r.getRecommendedUnitPrice(), r.getNetSavings(),
                        r.getBuyBefore(), r.getCreatedAt()))
                .toList();

        List<AiReport.PredictionLine> predictionLines = predictions.stream()
                .sorted(Comparator.comparingDouble((PricePrediction p) -> Math.abs(p.changePct().doubleValue()))
                        .reversed())
                .limit(MAX_LINES)
                .map(p -> new AiReport.PredictionLine(p.productReference(), p.productName(), p.currentPrice(),
                        p.predictedPrice(), p.changePct(), p.horizonDays(), p.confidence(),
                        p.confidenceLevel().label(), p.sampleSize()))
                .toList();

        List<AiReport.ComparisonLine> comparisons = new ArrayList<>();
        for (Long productId : quotes.productIdsWithQuotes().stream().sorted().limit(MAX_LINES).toList()) {
            ComparisonView c;
            try {
                c = quotes.compare(productId, null);
            } catch (ResourceNotFoundException e) {
                continue; // article supprimé du catalogue
            }
            c.offers().stream().filter(ComparisonView.Offer::recommended).findFirst().ifPresent(best ->
                    comparisons.add(new AiReport.ComparisonLine(c.productReference(), c.productName(),
                            c.offers().size(), best.supplierName(), best.offeredName(),
                            best.equivalence() == ma.pfe.stock.procurement.domain.model.Equivalence.EQUIVALENT,
                            best.unitPrice(), c.referencePrice(), best.savingPct(), best.annualSaving())));
        }

        long accepted = recos.stream().filter(r -> r.getStatus() == RecommendationStatus.ACCEPTED).count();
        long dismissed = recos.stream().filter(r -> r.getStatus() == RecommendationStatus.DISMISSED).count();
        long highConfidence = predictions.stream().filter(p -> p.confidenceLevel() == ConfidenceLevel.HIGH).count();
        AiReport.Kpis kpis = new AiReport.Kpis(ov.openOpportunities(), accepted, dismissed, ov.potentialSavings(),
                ov.realizedSavings(), savings.supplierSwitchGains(), savings.anticipatedPurchaseGains(),
                predictions.size(), ov.risingPrices(), ov.fallingPrices(), highConfidence, comparisons.size(),
                ov.candidateSuppliers(), ov.validatedSuppliers(), ov.draftRfqs(), ov.sentRfqs());

        return new AiReport(Instant.now(), Objects.requireNonNullElse(user, "-"), ov.currency(), ov.marketName(),
                ov.aiEnabled(), kpis, recoLines, predictionLines, comparisons, amounts(savings.bySupplier()),
                amounts(savings.byMonth()), ov.lastRun());
    }

    private static List<AiReport.AmountLine> amounts(List<SavingsCalculator.Line> lines) {
        return lines.stream().limit(MAX_LINES)
                .map(l -> new AiReport.AmountLine(l.label(), l.realized(), l.potential(), l.count())).toList();
    }

    private static String statusLabel(RecommendationStatus status) {
        return switch (status) {
            case OPEN -> "À décider";
            case ACCEPTED -> "Acceptée";
            case DISMISSED -> "Écartée";
            case EXPIRED -> "Expirée";
        };
    }
}
