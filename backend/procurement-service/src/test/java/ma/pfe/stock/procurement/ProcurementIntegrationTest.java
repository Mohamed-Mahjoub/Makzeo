package ma.pfe.stock.procurement;

import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import ma.pfe.stock.procurement.domain.model.WatchRun;
import ma.pfe.stock.procurement.domain.port.MarketSupplierRepository;
import ma.pfe.stock.procurement.domain.port.PricePointRepository;
import ma.pfe.stock.procurement.domain.port.RfqRepository;
import ma.pfe.stock.procurement.domain.port.WatchRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Démarre le service complet sur une vraie base PostgreSQL (conteneur jetable) : migrations Flyway,
 * validation du schéma par Hibernate et persistance des agrégats. Ignoré si Docker n'est pas disponible.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///procurement_db",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "app.security.jwt-secret=test-secret-for-integration-tests-0123456789",
        "app.procurement.watch-enabled=false"
})
@EnabledIf("dockerAvailable")
class ProcurementIntegrationTest {

    @Autowired
    private MarketSupplierRepository suppliers;
    @Autowired
    private RfqRepository rfqs;
    @Autowired
    private PricePointRepository prices;
    @Autowired
    private WatchRunRepository runs;

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    @Test
    void persistsSuppliersRfqsAndPriceHistory() {
        MarketSupplier supplier = suppliers.save(MarketSupplier.discovered("Fournisseur Test SARL", "test.tn",
                "ventes@test.tn", null, "Tunis"));
        assertThat(suppliers.findByNormalizedKey("fournisseurtest")).isPresent();
        supplier.validate();
        supplier = suppliers.save(supplier);
        assertThat(suppliers.countByStatus(MarketSupplierStatus.VALIDATED)).isGreaterThanOrEqualTo(1);

        Rfq rfq = rfqs.save(Rfq.draft(1L, "PRD-1", "Produit", supplier, 10, "Demande", "Texte", LocalDate.now(), "test"));
        assertThat(rfqs.existsOpen(1L, supplier.getId(), List.of(RfqStatus.DRAFT))).isTrue();
        assertThat(rfqs.search(RfqStatus.DRAFT, 1L, 0, 10).totalElements()).isEqualTo(1);
        assertThat(rfqs.findById(rfq.getId()).orElseThrow().getReference()).isEqualTo(rfq.getReference());

        prices.save(new PricePoint(null, 1L, new BigDecimal("12.500"), LocalDate.of(2026, 1, 5), PriceSource.PURCHASE,
                "Fournisseur Test", 10));
        prices.save(new PricePoint(null, 1L, new BigDecimal("13.000"), LocalDate.of(2026, 3, 5), PriceSource.PURCHASE,
                "Fournisseur Test", 10));
        assertThat(prices.findLatest(1L, List.of(PriceSource.PURCHASE)).orElseThrow().unitPrice())
                .isEqualByComparingTo("13.000");
        assertThat(prices.findByProduct(1L)).hasSize(2);
        prices.save(new PricePoint(null, 2L, new BigDecimal("4.000"), LocalDate.of(2026, 3, 5), PriceSource.CATALOG,
                null, null));
        assertThat(prices.productIdsWithSources(List.of(PriceSource.PURCHASE, PriceSource.QUOTE)))
                .contains(1L).doesNotContain(2L);
    }

    @Test
    void onlyOneWatchRunCanBeRunningAtATime() {
        WatchRun first = runs.save(WatchRun.start("2026-11", WatchRun.Trigger.MANUAL, "test"));
        assertThatThrownBy(() -> runs.save(WatchRun.start("2026-11", WatchRun.Trigger.SCHEDULED, "test")))
                .isInstanceOf(DataIntegrityViolationException.class);
        first.complete(new WatchRun.Stats(1, 0, 0, 0, 0), null);
        runs.save(first);
        assertThat(runs.existsCompleted("2026-11")).isTrue();
        assertThat(runs.findRunning()).isEmpty();
    }
}
