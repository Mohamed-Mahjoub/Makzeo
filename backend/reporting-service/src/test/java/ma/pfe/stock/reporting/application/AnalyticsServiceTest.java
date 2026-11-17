package ma.pfe.stock.reporting.application;

import ma.pfe.stock.reporting.application.dto.AnalyticsView;
import ma.pfe.stock.reporting.application.port.CatalogGateway;
import ma.pfe.stock.reporting.application.port.StockGateway;
import ma.pfe.stock.reporting.application.service.AnalyticsService;
import ma.pfe.stock.reporting.domain.model.AlertData;
import ma.pfe.stock.reporting.domain.model.CurrencyInfo;
import ma.pfe.stock.reporting.domain.model.InventoryData;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.ProductData;
import ma.pfe.stock.reporting.domain.model.StockLevelData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsServiceTest {

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("Africa/Casablanca");
        CatalogGateway catalog = (categoryId, active) -> List.of(
                // 100 en stock, 60 sorties en 30 j -> 2/j -> couverture 50 j -> NORMALE
                new ProductData(1L, "A", "Papier", "Bureau", "b1", "PAQUET", new BigDecimal("10.00"),
                        new BigDecimal("15.00"), 10, null, true),
                // 6 en stock (<= seuil 10), 30 sorties -> 1/j -> 6 j -> HAUTE ; cible max 50 -> 44
                new ProductData(2L, "B", "Stylo", "Bureau", "b2", "PIECE", new BigDecimal("2.00"),
                        new BigDecimal("4.00"), 10, 50, true),
                // 0 en stock, 90 sorties -> 3/j -> CRITIQUE ; CA perdu 3 x 20 = 60/j
                new ProductData(3L, "C", "Câble", "Électricité", "b3", "PIECE", new BigDecimal("12.00"),
                        new BigDecimal("20.00"), 5, null, true));
        StockGateway stock = new StockGateway() {
            public List<StockLevelData> findLevels(Collection<Long> ids) {
                return List.of(new StockLevelData(1L, 100, "OK"), new StockLevelData(2L, 6, "LOW"));
            }

            public List<MovementData> findMovements(LocalDate from, LocalDate to, String type, Long productId) {
                Instant now = Instant.now();
                return List.of(
                        new MovementData(1L, 1L, "A", "Papier", "IN", "Entrée", 160, 160, 0, 160, null, null, "u", now),
                        new MovementData(2L, 1L, "A", "Papier", "OUT", "Sortie", 60, -60, 160, 100, null, null, "u", now),
                        new MovementData(3L, 2L, "B", "Stylo", "OUT", "Sortie", 30, -30, 36, 6, null, null, "u", now),
                        new MovementData(4L, 3L, "C", "Câble", "OUT", "Sortie", 90, -90, 90, 0, null, null, "u", now));
            }

            public List<AlertData> findAlerts(boolean activeOnly) {
                return List.of(new AlertData(1L, "C", "Câble", "RUPTURE", "Rupture", "OPEN", 0, 5, Instant.now()),
                        new AlertData(2L, "B", "Stylo", "SEUIL_MIN", "Seuil", "OPEN", 6, 10, Instant.now()));
            }

            public InventoryData findInventory(Long id) {
                throw new UnsupportedOperationException();
            }
        };
        service = new AnalyticsService(catalog, stock, () -> CurrencyInfo.DEFAULT, zone);
    }

    @Test
    void computesKpis() {
        AnalyticsView.Kpis k = service.compute(30, null).kpis();

        assertThat(k.productCount()).isEqualTo(3);
        assertThat(k.totalQuantity()).isEqualTo(106);
        assertThat(k.stockValue()).isEqualByComparingTo("1012.00");
        assertThat(k.outOfStock()).isEqualTo(1);
        assertThat(k.lowStock()).isEqualTo(1);
        assertThat(k.activeAlerts()).isEqualTo(2);
        assertThat(k.entriesQuantity()).isEqualTo(160);
        assertThat(k.exitsQuantity()).isEqualTo(180);
        assertThat(k.lostSalesPerDay()).isEqualByComparingTo("60.00");
        assertThat(k.productsToReorder()).isEqualTo(2);
        assertThat(k.coverageDays()).isEqualByComparingTo("17.7");
    }

    @Test
    void replenishmentIsPrioritized() {
        List<AnalyticsView.Replenishment> r = service.compute(30, null).replenishment();

        assertThat(r).extracting(AnalyticsView.Replenishment::reference).containsExactly("C", "B", "A");
        assertThat(r.get(0).priority()).isEqualTo("CRITIQUE");
        assertThat(r.get(0).suggestedQuantity()).isEqualTo(90);   // 3/j x 30 j
        assertThat(r.get(1).priority()).isEqualTo("HAUTE");
        assertThat(r.get(1).coverageDays()).isEqualByComparingTo("6.0");
        assertThat(r.get(1).suggestedQuantity()).isEqualTo(44);   // stock max 50 - 6
        assertThat(r.get(1).estimatedRuptureDate()).isEqualTo(LocalDate.now(ZoneId.of("Africa/Casablanca")).plusDays(6));
        assertThat(r.get(2).priority()).isEqualTo("NORMALE");
        assertThat(r.get(2).suggestedQuantity()).isZero();
    }

    @Test
    void buildsChartsData() {
        AnalyticsView view = service.compute(30, null);

        assertThat(view.dailyFlows()).hasSize(30);
        assertThat(view.dailyFlows().getLast().exits()).isEqualTo(180);
        assertThat(view.valueByCategory().getFirst().category()).isEqualTo("Bureau");
        assertThat(view.topValue().getFirst().reference()).isEqualTo("A");
        assertThat(view.topConsumed().getFirst().reference()).isEqualTo("C");
        assertThat(view.statusDistribution()).extracting(AnalyticsView.StatusSlice::count).containsExactly(1, 1, 1);
    }

    @Test
    void rejectsInvalidPeriod() {
        assertThatThrownBy(() -> service.compute(2, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
