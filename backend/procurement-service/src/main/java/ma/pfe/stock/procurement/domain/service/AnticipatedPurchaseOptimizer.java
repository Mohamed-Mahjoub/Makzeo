package ma.pfe.stock.procurement.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Quantité optimale d'un achat anticipé avant une hausse de prix.
 * <p>
 * Acheter maintenant une unité consommée dans m mois coûte p0 x (1 + h x m) (prix actuel + stockage),
 * contre p1 si on l'achète après la hausse. L'achat anticipé est rentable tant que
 * m &lt; (p1 / p0 - 1) / h. On couvre donc la consommation jusqu'à cette limite (bornée par une
 * couverture maximale), moins le stock déjà disponible, puis on applique les contraintes : minimum de
 * commande, capacité de stockage et budget. Le gain est ensuite simulé mois par mois.
 */
public final class AnticipatedPurchaseOptimizer {

    /**
     * Données du calcul.
     *
     * @param monthsUntilIncrease délai avant la hausse prévue (horizon de la prédiction)
     * @param monthlyHoldingRate  coût de stockage mensuel en fraction de la valeur (0,015 = 1,5 %/mois)
     * @param maxCoverMonths      couverture maximale autorisée (risque d'obsolescence)
     * @param budget              budget disponible (null : illimité)
     * @param storageCapacity     stock maximal admissible (null : illimité)
     */
    public record Input(int currentStock, double monthlyConsumption, BigDecimal priceNow, BigDecimal futurePrice,
                        int monthsUntilIncrease, double monthlyHoldingRate, int maxCoverMonths, BigDecimal budget,
                        int minOrderQuantity, Integer storageCapacity) {
    }

    /**
     * Plan d'achat recommandé.
     *
     * @param costNow     coût d'achat immédiat de la quantité
     * @param holdingCost coût de stockage induit
     * @param costLater   coût des mêmes unités achetées au fil des besoins (après hausse le cas échéant)
     * @param netSavings  costLater - costNow - holdingCost
     */
    public record Plan(int quantity, int coverMonths, BigDecimal costNow, BigDecimal holdingCost,
                       BigDecimal costLater, BigDecimal netSavings) {
    }

    private static final int MAX_SIMULATION_MONTHS = 60;

    private AnticipatedPurchaseOptimizer() {
    }

    /** Renvoie un plan seulement si l'achat anticipé fait réellement économiser de l'argent. */
    public static Optional<Plan> optimize(Input in) {
        if (in.priceNow() == null || in.futurePrice() == null || in.priceNow().signum() <= 0
                || in.futurePrice().compareTo(in.priceNow()) <= 0 || in.monthlyConsumption() <= 0) {
            return Optional.empty();
        }
        double p0 = in.priceNow().doubleValue();
        double p1 = in.futurePrice().doubleValue();
        double h = Math.max(0, in.monthlyHoldingRate());
        int horizon = Math.max(0, in.monthsUntilIncrease());

        double breakEvenMonths = h == 0 ? Double.MAX_VALUE : (p1 / p0 - 1) / h;
        int coverMonths = (int) Math.min(in.maxCoverMonths(), Math.floor(breakEvenMonths));
        if (coverMonths <= horizon) {
            // le coût de stockage absorbe le gain avant même que la hausse ne survienne
            return Optional.empty();
        }

        long need = (long) Math.ceil(in.monthlyConsumption() * coverMonths) - Math.max(0, in.currentStock());
        if (need <= 0) {
            return Optional.empty();
        }
        long qty = Math.max(need, in.minOrderQuantity());
        if (in.storageCapacity() != null) {
            qty = Math.min(qty, (long) in.storageCapacity() - Math.max(0, in.currentStock()));
        }
        if (in.budget() != null) {
            qty = Math.min(qty, (long) Math.floor(in.budget().doubleValue() / p0));
        }
        if (qty <= 0 || qty < in.minOrderQuantity()) {
            return Optional.empty();
        }

        // simulation mensuelle : le stock existant est consommé en premier
        double existing = Math.max(0, in.currentStock());
        double remaining = qty;
        double costNow = qty * p0;
        double holding = 0;
        double costLater = 0;
        for (int m = 1; m <= MAX_SIMULATION_MONTHS && remaining > 0; m++) {
            double demand = in.monthlyConsumption();
            double fromExisting = Math.min(existing, demand);
            existing -= fromExisting;
            double fromBatch = Math.min(remaining, demand - fromExisting);
            remaining -= fromBatch;
            holding += fromBatch * p0 * h * (m - 0.5);
            costLater += fromBatch * (m <= horizon ? p0 : p1);
        }
        // unités non consommées dans la fenêtre simulée : valorisées au prix futur, stockage maximal
        if (remaining > 0) {
            holding += remaining * p0 * h * MAX_SIMULATION_MONTHS;
            costLater += remaining * p1;
        }
        double net = costLater - costNow - holding;
        if (net <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Plan((int) qty, coverMonths, money(costNow), money(holding), money(costLater),
                money(net)));
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }
}
