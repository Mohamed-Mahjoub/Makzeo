package ma.pfe.stock.stockmgmt.application;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.stockmgmt.application.dto.MovementCommand;
import ma.pfe.stock.stockmgmt.application.dto.MovementView;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.application.service.AlertService;
import ma.pfe.stock.stockmgmt.application.service.StockMovementService;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.InsufficientStockException;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Scénarios métier complets : mouvements + alertes automatiques. */
class StockMovementServiceTest {

    private InMemoryStockFixtures.Items items;
    private InMemoryStockFixtures.Movements movements;
    private InMemoryStockFixtures.Alerts alerts;
    private ProductCatalog catalog;
    private StockMovementService service;

    @BeforeEach
    void setUp() {
        items = new InMemoryStockFixtures.Items();
        movements = new InMemoryStockFixtures.Movements();
        alerts = new InMemoryStockFixtures.Alerts();
        catalog = mock(ProductCatalog.class);
        when(catalog.getProduct(1L)).thenReturn(new ProductSnapshot(1L, "PRD-1", "Papier A4", 10, true));
        when(catalog.getProduct(2L)).thenReturn(new ProductSnapshot(2L, "OLD-1", "Ancien produit", 0, false));
        AlertService alertService = new AlertService(alerts, items, catalog);
        service = new StockMovementService(items, movements, catalog, alertService);
    }

    private MovementView move(MovementType type, int qty) {
        return service.record(new MovementCommand(1L, type, qty, null, "BL-1", null), "mag");
    }

    @Test
    void entryThenExitUpdatesStockAndHistory() {
        move(MovementType.IN, 100);
        MovementView out = move(MovementType.OUT, 30);

        assertThat(out.quantityBefore()).isEqualTo(100);
        assertThat(out.quantityAfter()).isEqualTo(70);
        assertThat(out.signedQuantity()).isEqualTo(-30);
        assertThat(out.performedBy()).isEqualTo("mag");
        assertThat(items.byProduct.get(1L).getQuantity()).isEqualTo(70);
        assertThat(movements.saved).hasSize(2);
        assertThat(alerts.active()).isEmpty();
    }

    @Test
    void exitGreaterThanStockIsRefusedAndNothingIsRecorded() {
        move(MovementType.IN, 5);

        assertThatThrownBy(() -> move(MovementType.OUT, 6)).isInstanceOf(InsufficientStockException.class);
        assertThat(movements.saved).hasSize(1);
    }

    @Test
    void alertsAreRaisedEscalatedAndResolvedAutomatically() {
        move(MovementType.IN, 15);
        move(MovementType.OUT, 7);          // 8 <= 10 : seuil minimum
        assertThat(alerts.active()).singleElement()
                .satisfies(a -> assertThat(a.getType()).isEqualTo(AlertType.SEUIL_MIN));

        move(MovementType.OUT, 8);          // 0 : rupture (la précédente est résolue)
        assertThat(alerts.active()).singleElement()
                .satisfies(a -> assertThat(a.getType()).isEqualTo(AlertType.RUPTURE));
        assertThat(alerts.byId.values()).filteredOn(a -> a.getStatus() == AlertStatus.RESOLVED).hasSize(1);

        move(MovementType.IN, 50);          // réapprovisionnement : plus d'alerte
        assertThat(alerts.active()).isEmpty();
    }

    @Test
    void movementOnInactiveProductIsRefused() {
        assertThatThrownBy(() -> service.record(new MovementCommand(2L, MovementType.IN, 1, null, null, null), "mag"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("désactivé");
    }

    @Test
    void manualAdjustmentIsRefused() {
        assertThatThrownBy(() -> move(MovementType.ADJUSTMENT, 3)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void inventoryAdjustmentCreatesSignedMovement() {
        move(MovementType.IN, 20);
        ProductSnapshot product = catalog.getProduct(1L);

        MovementView adjustment = service.adjust(product, 17, 4L, "Inventaire", "chef");

        assertThat(adjustment.type()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(adjustment.quantity()).isEqualTo(3);
        assertThat(adjustment.signedQuantity()).isEqualTo(-3);
        assertThat(adjustment.inventoryId()).isEqualTo(4L);
        assertThat(service.adjust(product, 17, 4L, "Inventaire", "chef")).isNull();
    }
}
