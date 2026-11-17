package ma.pfe.stock.reporting.application.service;

import ma.pfe.stock.reporting.application.dto.AnalyticsView;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.CategoryValue;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.DailyFlow;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.Kpis;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.ProductConsumption;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.ProductValue;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.Replenishment;
import ma.pfe.stock.reporting.application.dto.AnalyticsView.StatusSlice;
import ma.pfe.stock.reporting.application.port.CatalogGateway;
import ma.pfe.stock.reporting.application.port.SettingsGateway;
import ma.pfe.stock.reporting.application.port.StockGateway;
import ma.pfe.stock.reporting.domain.model.CurrencyInfo;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.ProductData;
import ma.pfe.stock.reporting.domain.model.StockLevelData;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cas d'utilisation : indicateurs décisionnels du tableau de bord.
 * Agrège le catalogue (product-service) et le stock (stock-service) ; aucune donnée n'est stockée.
 */
@Service
public class AnalyticsService {

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 365;
    private static final int TOP_SIZE = 10;
    /** Horizon de couverture visé lors du calcul de la quantité suggérée (jours). */
    private static final int TARGET_COVERAGE_DAYS = 30;

    private final CatalogGateway catalogGateway;
    private final StockGateway stockGateway;
    private final SettingsGateway settingsGateway;
    private final ZoneId zoneId;

    public AnalyticsService(CatalogGateway catalogGateway, StockGateway stockGateway, SettingsGateway settingsGateway,
                            ZoneId zoneId) {
        this.catalogGateway = catalogGateway;
        this.stockGateway = stockGateway;
        this.settingsGateway = settingsGateway;
        this.zoneId = zoneId;
    }

    public AnalyticsView compute(int days, Long categoryId) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("La période doit être comprise entre " + MIN_DAYS + " et "
                    + MAX_DAYS + " jours");
        }
        CurrencyInfo currency = settingsGateway.currency();
        LocalDate to = LocalDate.now(zoneId);
        LocalDate from = to.minusDays(days - 1L);

        List<ProductData> products = catalogGateway.findAllProducts(categoryId, Boolean.TRUE);
        Map<Long, ProductData> productsById = products.stream()
                .collect(Collectors.toMap(ProductData::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<Long, Integer> quantities = stockGateway.findLevels(productsById.keySet()).stream()
                .collect(Collectors.toMap(StockLevelData::productId, StockLevelData::quantity, (a, b) -> a));
        List<MovementData> movements = stockGateway.findMovements(from, to, null, null).stream()
                .filter(m -> productsById.containsKey(m.productId()))
                .toList();
        Set<String> references = products.stream().map(ProductData::reference)
                .collect(Collectors.toSet());
        int activeAlerts = (int) stockGateway.findAlerts(true).stream()
                .filter(a -> references.contains(a.productReference()))
                .count();

        // --- Flux quotidiens (entrées / sorties / ajustements) et consommation par produit
        Map<LocalDate, long[]> perDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            perDay.put(d, new long[3]);
        }
        Map<Long, Long> exitsByProduct = new HashMap<>();
        long entries = 0;
        long exits = 0;
        for (MovementData m : movements) {
            LocalDate day = m.occurredAt().atZone(zoneId).toLocalDate();
            long[] bucket = perDay.get(day);
            switch (m.type()) {
                case "IN" -> {
                    entries += m.quantity();
                    if (bucket != null) {
                        bucket[0] += m.quantity();
                    }
                }
                case "OUT" -> {
                    exits += m.quantity();
                    if (bucket != null) {
                        bucket[1] += m.quantity();
                    }
                    exitsByProduct.merge(m.productId(), (long) m.quantity(), Long::sum);
                }
                default -> {
                    if (bucket != null) {
                        bucket[2] += m.signedQuantity();
                    }
                }
            }
        }
        List<DailyFlow> dailyFlows = perDay.entrySet().stream()
                .map(e -> new DailyFlow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();

        // --- État, valorisation, réapprovisionnement
        long totalQuantity = 0;
        BigDecimal stockValue = BigDecimal.ZERO;
        BigDecimal lostSalesPerDay = BigDecimal.ZERO;
        int outOfStock = 0;
        int low = 0;
        Map<String, long[]> categoryQty = new TreeMap<>();
        Map<String, BigDecimal> categoryValue = new TreeMap<>();
        List<ProductValue> values = new ArrayList<>();
        List<Replenishment> replenishment = new ArrayList<>();
        for (ProductData p : products) {
            int qty = quantities.getOrDefault(p.id(), 0);
            BigDecimal price = orZero(p.purchasePrice());
            BigDecimal value = currency.round(price.multiply(BigDecimal.valueOf(qty)));
            String category = p.categoryName() == null ? "Sans catégorie" : p.categoryName();
            totalQuantity += qty;
            stockValue = stockValue.add(value);
            categoryQty.computeIfAbsent(category, k -> new long[2]);
            categoryQty.get(category)[0]++;
            categoryQty.get(category)[1] += qty;
            categoryValue.merge(category, value, BigDecimal::add);
            values.add(new ProductValue(p.id(), p.reference(), p.name(), category, qty, price, value));

            BigDecimal avgDaily = BigDecimal.valueOf(exitsByProduct.getOrDefault(p.id(), 0L))
                    .divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
            if (qty <= 0) {
                outOfStock++;
                lostSalesPerDay = lostSalesPerDay.add(avgDaily.multiply(orZero(p.salePrice())));
            } else if (qty <= p.minStock()) {
                low++;
            }
            replenishment.add(replenishmentOf(p, qty, avgDaily, category, to, currency));
        }

        replenishment.sort(Comparator.comparingInt((Replenishment r) -> priorityRank(r.priority()))
                .thenComparing(r -> r.coverageDays() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : r.coverageDays())
                .thenComparing(Replenishment::reference));
        List<Replenishment> toReorder = replenishment.stream().filter(r -> !"NORMALE".equals(r.priority())).toList();
        BigDecimal reorderValue = toReorder.stream().map(Replenishment::suggestedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryValue> byCategory = categoryQty.entrySet().stream()
                .map(e -> new CategoryValue(e.getKey(), (int) e.getValue()[0], e.getValue()[1],
                        currency.round(categoryValue.get(e.getKey()))))
                .sorted(Comparator.comparing(CategoryValue::value).reversed())
                .toList();
        List<ProductValue> topValue = values.stream()
                .sorted(Comparator.comparing(ProductValue::value).reversed())
                .limit(TOP_SIZE).toList();
        List<ProductConsumption> topConsumed = exitsByProduct.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(TOP_SIZE)
                .map(e -> {
                    ProductData p = productsById.get(e.getKey());
                    return new ProductConsumption(p.id(), p.reference(), p.name(), e.getValue(),
                            BigDecimal.valueOf(e.getValue()).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP));
                })
                .toList();

        int ok = products.size() - outOfStock - low;
        List<StatusSlice> status = List.of(
                new StatusSlice("OK", "Normal", ok),
                new StatusSlice("LOW", "Sous le seuil", low),
                new StatusSlice("OUT_OF_STOCK", "Rupture", outOfStock));

        BigDecimal avgDailyExitsTotal = BigDecimal.valueOf(exits).divide(BigDecimal.valueOf(days), 4,
                RoundingMode.HALF_UP);
        BigDecimal coverage = avgDailyExitsTotal.signum() == 0 ? null
                : BigDecimal.valueOf(totalQuantity).divide(avgDailyExitsTotal, 1, RoundingMode.HALF_UP);
        BigDecimal rotation = BigDecimal.valueOf(exits)
                .divide(BigDecimal.valueOf(Math.max(1, totalQuantity)), 2, RoundingMode.HALF_UP);

        Kpis kpis = new Kpis(products.size(), totalQuantity, currency.round(stockValue),
                outOfStock, low, activeAlerts, entries, exits, movements.size(), rotation, coverage,
                currency.round(lostSalesPerDay), toReorder.size(), currency.round(reorderValue));

        return new AnalyticsView(Instant.now(), days, currency.code(), currency.fractionDigits(), from, to, kpis, dailyFlows, status, byCategory, topValue,
                topConsumed, replenishment);
    }

    private Replenishment replenishmentOf(ProductData p, int qty, BigDecimal avgDaily, String category,
                                          LocalDate today, CurrencyInfo currency) {
        BigDecimal coverage = null;
        LocalDate ruptureDate = null;
        if (avgDaily.signum() > 0) {
            coverage = BigDecimal.valueOf(qty).divide(avgDaily, 1, RoundingMode.HALF_UP);
            ruptureDate = today.plusDays(coverage.setScale(0, RoundingMode.DOWN).longValue());
        }
        String priority;
        if (qty <= 0) {
            priority = "CRITIQUE";
        } else if (qty <= p.minStock() || (coverage != null && coverage.compareTo(BigDecimal.valueOf(7)) < 0)) {
            priority = "HAUTE";
        } else if (coverage != null && coverage.compareTo(BigDecimal.valueOf(15)) < 0) {
            priority = "MOYENNE";
        } else {
            priority = "NORMALE";
        }
        int consumptionTarget = avgDaily.multiply(BigDecimal.valueOf(TARGET_COVERAGE_DAYS))
                .setScale(0, RoundingMode.CEILING).intValue();
        int target = p.maxStock() != null ? p.maxStock() : Math.max(p.minStock() * 2, consumptionTarget);
        int suggested = "NORMALE".equals(priority) ? 0 : Math.max(0, target - qty);
        BigDecimal suggestedValue = currency.round(orZero(p.purchasePrice()).multiply(BigDecimal.valueOf(suggested)));
        return new Replenishment(p.id(), p.reference(), p.name(), category, qty, p.minStock(), p.maxStock(),
                avgDaily, coverage, ruptureDate, suggested, suggestedValue, priority);
    }

    private static int priorityRank(String priority) {
        return switch (priority) {
            case "CRITIQUE" -> 0;
            case "HAUTE" -> 1;
            case "MOYENNE" -> 2;
            default -> 3;
        };
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
