package ma.pfe.stock.stockmgmt.domain;

import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.InsufficientStockException;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;
import ma.pfe.stock.stockmgmt.domain.service.AlertPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockItemTest {

    private static StockItem item(int minStock) {
        return StockItem.empty(new ProductSnapshot(1L, "PRD-1", "Produit", minStock, true));
    }

    @Test
    void increaseAndDecreaseReturnPreviousQuantity() {
        StockItem item = item(5);
        assertThat(item.increase(20)).isZero();
        assertThat(item.decrease(8)).isEqualTo(20);
        assertThat(item.getQuantity()).isEqualTo(12);
        assertThat(item.getLastMovementAt()).isNotNull();
    }

    @Test
    void cannotGoBelowZero() {
        StockItem item = item(5);
        item.increase(3);
        assertThatThrownBy(() -> item.decrease(4))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("disponible 3, demandé 4");
        assertThat(item.getQuantity()).isEqualTo(3);
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertThatThrownBy(() -> item(0).increase(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> item(0).decrease(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void statusAndAlertPolicyFollowThresholds() {
        StockItem item = item(10);
        assertThat(item.status()).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(AlertPolicy.evaluate(item)).contains(AlertType.RUPTURE);

        item.increase(10);
        assertThat(item.status()).isEqualTo(StockStatus.LOW);
        assertThat(AlertPolicy.evaluate(item)).contains(AlertType.SEUIL_MIN);

        item.increase(1);
        assertThat(item.status()).isEqualTo(StockStatus.OK);
        assertThat(AlertPolicy.evaluate(item)).isEmpty();
    }

    @Test
    void adjustToSetsAbsoluteQuantity() {
        StockItem item = item(0);
        item.increase(10);
        assertThat(item.adjustTo(7)).isEqualTo(10);
        assertThat(item.getQuantity()).isEqualTo(7);
    }
}
