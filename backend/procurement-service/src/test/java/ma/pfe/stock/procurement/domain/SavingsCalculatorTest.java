package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SavingsCalculatorTest {

    private static PurchaseRecommendation rec(long productId, RecommendationType type, RecommendationStatus status,
                                              String supplier, String savings) {
        return new PurchaseRecommendation(null, type, status, productId, "PRD-" + productId, "Produit " + productId,
                supplier, null, 10, BigDecimal.TEN, BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, new BigDecimal(savings), null, null, null, Instant.parse("2026-10-05T10:00:00Z"),
                null, null, null);
    }

    @Test
    void switchSavingsIsZeroWhenTheNewOfferIsNotCheaper() {
        assertThat(SavingsCalculator.switchSavings(new BigDecimal("100"), new BigDecimal("92"), 120))
                .isEqualByComparingTo("960");
        assertThat(SavingsCalculator.switchSavings(new BigDecimal("100"), new BigDecimal("105"), 120))
                .isEqualByComparingTo("0");
    }

    @Test
    void summarizesRealizedAndPotentialSavings() {
        SavingsCalculator.Summary s = SavingsCalculator.summarize(List.of(
                rec(1, RecommendationType.SWITCH_SUPPLIER, RecommendationStatus.ACCEPTED, "A", "500"),
                rec(1, RecommendationType.ANTICIPATED_PURCHASE, RecommendationStatus.OPEN, "A", "120"),
                rec(2, RecommendationType.SWITCH_SUPPLIER, RecommendationStatus.OPEN, "B", "80"),
                rec(3, RecommendationType.SWITCH_SUPPLIER, RecommendationStatus.DISMISSED, "C", "999")),
                ZoneId.of("Africa/Tunis"));
        assertThat(s.totalRealized()).isEqualByComparingTo("500");
        assertThat(s.totalPotential()).isEqualByComparingTo("200");
        assertThat(s.supplierSwitchGains()).isEqualByComparingTo("580");
        assertThat(s.anticipatedPurchaseGains()).isEqualByComparingTo("120");
        assertThat(s.byProduct()).hasSize(2);
        assertThat(s.byProduct().get(0).label()).isEqualTo("PRD-1 - Produit 1");
        assertThat(s.bySupplier()).extracting(SavingsCalculator.Line::key).containsExactly("A", "B");
        assertThat(s.byMonth()).extracting(SavingsCalculator.Line::key).containsExactly("2026-10");
    }
}
