package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.procurement.domain.service.AnticipatedPurchaseOptimizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnticipatedPurchaseOptimizerTest {

    private static AnticipatedPurchaseOptimizer.Input input(int stock, String now, String future, BigDecimal budget) {
        return new AnticipatedPurchaseOptimizer.Input(stock, 10, new BigDecimal(now), new BigDecimal(future), 3,
                0.015, 12, budget, 1, null);
    }

    @Test
    void buysUpToTheBreakEvenPointOfHoldingCosts() {
        // hausse de 10 % dans 3 mois, stockage 1,5 %/mois : rentable jusqu'à 6,67 mois de couverture
        AnticipatedPurchaseOptimizer.Plan plan = AnticipatedPurchaseOptimizer.optimize(input(0, "100", "110", null))
                .orElseThrow();
        assertThat(plan.coverMonths()).isEqualTo(6);
        assertThat(plan.quantity()).isEqualTo(60);
        assertThat(plan.costNow()).isEqualByComparingTo("6000");
        assertThat(plan.holdingCost()).isEqualByComparingTo("270");
        assertThat(plan.costLater()).isEqualByComparingTo("6300");
        assertThat(plan.netSavings()).isEqualByComparingTo("30");
    }

    @Test
    void existingStockIsConsumedFirst() {
        AnticipatedPurchaseOptimizer.Plan plan = AnticipatedPurchaseOptimizer.optimize(input(20, "100", "110", null))
                .orElseThrow();
        assertThat(plan.quantity()).isEqualTo(40);
        assertThat(plan.holdingCost()).isEqualByComparingTo("240");
        assertThat(plan.netSavings()).isEqualByComparingTo("60");
    }

    @Test
    void noRecommendationWhenPricesFallOrStockIsSufficient() {
        assertThat(AnticipatedPurchaseOptimizer.optimize(input(0, "100", "95", null))).isEmpty();
        assertThat(AnticipatedPurchaseOptimizer.optimize(input(100, "100", "110", null))).isEmpty();
    }

    @Test
    void noRecommendationWhenTheBudgetMakesItUnprofitable() {
        // 3 000 TND n'achètent que les besoins d'avant la hausse : aucun gain, seulement du stockage
        assertThat(AnticipatedPurchaseOptimizer.optimize(input(0, "100", "110", new BigDecimal("3000")))).isEmpty();
    }

    @Test
    void noRecommendationWhenHoldingCostsAbsorbTheIncrease() {
        // hausse de 3 % : rentable sur 2 mois seulement, alors que la hausse survient dans 3 mois
        assertThat(AnticipatedPurchaseOptimizer.optimize(input(0, "100", "103", null))).isEmpty();
    }
}
