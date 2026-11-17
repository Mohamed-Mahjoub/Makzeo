package ma.pfe.stock.reporting.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Données du tableau de bord décisionnel (inspiré des tableaux de bord BI "achats / stocks") :
 * état des stocks, prévision de réapprovisionnement et valorisation.
 */
public record AnalyticsView(
        Instant generatedAt,
        int periodDays,
        String currency,
        int currencyFractionDigits,
        LocalDate from,
        LocalDate to,
        Kpis kpis,
        List<DailyFlow> dailyFlows,
        List<StatusSlice> statusDistribution,
        List<CategoryValue> valueByCategory,
        List<ProductValue> topValue,
        List<ProductConsumption> topConsumed,
        List<Replenishment> replenishment) {

    /**
     * @param stockValue       valeur du stock au prix d'achat (MAD)
     * @param rotationRate     taux de rotation sur la période = sorties / stock actuel
     * @param coverageDays     couverture globale (jours) = stock actuel / sorties moyennes par jour (null si aucune sortie)
     * @param lostSalesPerDay  chiffre d'affaires potentiellement perdu par jour sur les produits en rupture
     */
    public record Kpis(int productCount, long totalQuantity, BigDecimal stockValue, int outOfStock, int lowStock,
                       int activeAlerts, long entriesQuantity, long exitsQuantity, long movementCount,
                       BigDecimal rotationRate, BigDecimal coverageDays, BigDecimal lostSalesPerDay,
                       int productsToReorder, BigDecimal reorderValue) {
    }

    public record DailyFlow(LocalDate date, long entries, long exits, long adjustments) {
    }

    public record StatusSlice(String status, String label, int count) {
    }

    public record CategoryValue(String category, int productCount, long quantity, BigDecimal value) {
    }

    public record ProductValue(Long productId, String reference, String name, String category, int quantity,
                               BigDecimal unitPrice, BigDecimal value) {
    }

    public record ProductConsumption(Long productId, String reference, String name, long exits,
                                     BigDecimal avgDailyExits) {
    }

    /**
     * @param coverageDays          jours de stock restants au rythme actuel (null = pas de consommation)
     * @param estimatedRuptureDate  date estimée de rupture (null si pas de consommation)
     * @param suggestedQuantity     quantité à commander pour revenir au stock cible
     * @param priority              CRITIQUE (rupture), HAUTE (sous seuil ou &lt; 7 j), MOYENNE (&lt; 15 j), NORMALE
     */
    public record Replenishment(Long productId, String reference, String name, String category, int quantity,
                                int minStock, Integer maxStock, BigDecimal avgDailyExits, BigDecimal coverageDays,
                                LocalDate estimatedRuptureDate, int suggestedQuantity, BigDecimal suggestedValue,
                                String priority) {
    }
}
