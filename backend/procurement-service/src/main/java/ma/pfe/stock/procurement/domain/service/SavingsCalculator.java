package ma.pfe.stock.procurement.domain.service;

import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Économies réalisées (recommandations acceptées) et potentielles (recommandations ouvertes),
 * par article, par fournisseur et par mois.
 */
public final class SavingsCalculator {

    /** Une ligne d'agrégat. */
    public record Line(String key, String label, BigDecimal realized, BigDecimal potential, int count) {
    }

    /** Synthèse. */
    public record Summary(BigDecimal totalRealized, BigDecimal totalPotential, BigDecimal anticipatedPurchaseGains,
                          BigDecimal supplierSwitchGains, List<Line> byProduct, List<Line> bySupplier,
                          List<Line> byMonth) {
    }

    private SavingsCalculator() {
    }

    /** Économie annuelle d'un changement de fournisseur : écart de prix x consommation annuelle. */
    public static BigDecimal switchSavings(BigDecimal lastPrice, BigDecimal newPrice, int annualQuantity) {
        if (lastPrice == null || newPrice == null || annualQuantity <= 0) {
            return BigDecimal.ZERO.setScale(3);
        }
        BigDecimal diff = lastPrice.subtract(newPrice);
        return diff.signum() <= 0 ? BigDecimal.ZERO.setScale(3)
                : diff.multiply(BigDecimal.valueOf(annualQuantity)).setScale(3, RoundingMode.HALF_UP);
    }

    public static Summary summarize(List<PurchaseRecommendation> recommendations, ZoneId zone) {
        List<PurchaseRecommendation> relevant = recommendations.stream()
                .filter(r -> r.getStatus() == RecommendationStatus.ACCEPTED
                        || r.getStatus() == RecommendationStatus.OPEN)
                .toList();
        BigDecimal realized = sum(relevant, RecommendationStatus.ACCEPTED);
        BigDecimal potential = sum(relevant, RecommendationStatus.OPEN);
        BigDecimal anticipated = relevant.stream()
                .filter(r -> r.getType() == ma.pfe.stock.procurement.domain.model.RecommendationType.ANTICIPATED_PURCHASE)
                .map(PurchaseRecommendation::getNetSavings).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal switching = relevant.stream()
                .filter(r -> r.getType() == ma.pfe.stock.procurement.domain.model.RecommendationType.SWITCH_SUPPLIER)
                .map(PurchaseRecommendation::getNetSavings).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Line> byProduct = group(relevant, r -> String.valueOf(r.getProductId()),
                r -> r.getProductReference() + " - " + r.getProductName());
        List<Line> bySupplier = group(relevant,
                r -> r.getSupplierName() == null ? "-" : r.getSupplierName(),
                r -> r.getSupplierName() == null ? "Fournisseur actuel" : r.getSupplierName());
        List<Line> byMonth = group(relevant,
                r -> YearMonth.from((r.getDecidedAt() != null ? r.getDecidedAt() : r.getCreatedAt()).atZone(zone)).toString(),
                r -> YearMonth.from((r.getDecidedAt() != null ? r.getDecidedAt() : r.getCreatedAt()).atZone(zone)).toString());
        byMonth.sort(Comparator.comparing(Line::key));
        return new Summary(realized, potential, anticipated.setScale(3, RoundingMode.HALF_UP),
                switching.setScale(3, RoundingMode.HALF_UP), byProduct, bySupplier, byMonth);
    }

    private static BigDecimal sum(List<PurchaseRecommendation> list, RecommendationStatus status) {
        return list.stream().filter(r -> r.getStatus() == status).map(PurchaseRecommendation::getNetSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
    }

    private static List<Line> group(List<PurchaseRecommendation> list, Function<PurchaseRecommendation, String> key,
                                    Function<PurchaseRecommendation, String> label) {
        Map<String, BigDecimal[]> amounts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PurchaseRecommendation r : list) {
            String k = key.apply(r);
            labels.putIfAbsent(k, label.apply(r));
            BigDecimal[] a = amounts.computeIfAbsent(k, x -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (r.getStatus() == RecommendationStatus.ACCEPTED) {
                a[0] = a[0].add(r.getNetSavings());
            } else {
                a[1] = a[1].add(r.getNetSavings());
            }
            counts.merge(k, 1, Integer::sum);
        }
        List<Line> lines = new ArrayList<>();
        amounts.forEach((k, a) -> lines.add(new Line(k, labels.get(k), a[0].setScale(3, RoundingMode.HALF_UP),
                a[1].setScale(3, RoundingMode.HALF_UP), counts.get(k))));
        lines.sort(Comparator.comparing((Line l) -> l.realized().add(l.potential())).reversed());
        return lines;
    }
}
