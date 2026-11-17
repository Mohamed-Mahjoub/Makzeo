package ma.pfe.stock.stockmgmt.domain;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.stockmgmt.domain.model.Inventory;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    private static final ProductSnapshot P1 = new ProductSnapshot(1L, "P1", "Produit 1", 0, true);

    @Test
    void recountReplacesPreviousLine() {
        Inventory inv = Inventory.open("Inventaire annuel", null, "mag");
        inv.recordCount(P1, 10, 8, "mag");
        inv.recordCount(P1, 10, 9, "mag");

        assertThat(inv.getLines()).hasSize(1);
        assertThat(inv.getLines().getFirst().difference()).isEqualTo(-1);
    }

    @Test
    void cannotValidateEmptyInventory() {
        Inventory inv = Inventory.open("Vide", null, "mag");
        assertThatThrownBy(() -> inv.validate("chef")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closedInventoryIsReadOnly() {
        Inventory inv = Inventory.open("Test", null, "mag");
        inv.recordCount(P1, 5, 5, "mag");
        inv.validate("chef");

        assertThat(inv.getStatus()).isEqualTo(InventoryStatus.VALIDATED);
        assertThat(inv.getClosedBy()).isEqualTo("chef");
        assertThatThrownBy(() -> inv.recordCount(P1, 5, 4, "mag")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> inv.cancel("chef")).isInstanceOf(BusinessRuleException.class);
    }
}
