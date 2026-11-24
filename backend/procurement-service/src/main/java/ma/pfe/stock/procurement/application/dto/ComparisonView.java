package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.QuoteSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Comparaison des offres d'un article.
 *
 * @param referencePrice dernier prix payé (ou prix d'achat de la fiche produit)
 */
public record ComparisonView(Long productId, String productReference, String productName, int quantity,
                             BigDecimal referencePrice, String currency, double monthlyConsumption,
                             List<Offer> offers) {

    public record Offer(int rank, boolean eligible, String exclusionReason, boolean recommended, Long quoteId,
                        Long marketSupplierId, String supplierName, String offeredName, Equivalence equivalence,
                        BigDecimal unitPrice, int minQuantity, int orderQuantity, BigDecimal totalCost,
                        BigDecimal effectiveUnitCost, Integer leadTimeDays, Availability availability,
                        int qualityScore, LocalDate validUntil, QuoteSource source, double score,
                        BigDecimal unitSaving, BigDecimal savingPct, BigDecimal annualSaving) {
    }
}
