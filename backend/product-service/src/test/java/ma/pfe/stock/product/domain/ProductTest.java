package ma.pfe.stock.product.domain;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.product.domain.model.Barcode;
import ma.pfe.stock.product.domain.model.Product;
import ma.pfe.stock.product.domain.model.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static Product sample() {
        return Product.create(" prd-001 ", "Clavier USB", null, 1L, Barcode.internalEan13(1), null,
                new BigDecimal("80.00"), new BigDecimal("120.00"), 5, 50, null);
    }

    @Test
    void createsActiveProductWithNormalizedReferenceAndDefaultUnit() {
        Product product = sample();

        assertThat(product.getReference()).isEqualTo("PRD-001");
        assertThat(product.getUnit()).isEqualTo(UnitOfMeasure.PIECE);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    void rejectsNegativeMinStock() {
        assertThatThrownBy(() -> Product.create("P1", "X", null, 1L, Barcode.internalEan13(2), null, null, null,
                -1, null, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsMaxStockLowerThanMinStock() {
        assertThatThrownBy(() -> Product.create("P1", "X", null, 1L, Barcode.internalEan13(2), null, null, null,
                10, 5, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> Product.create("P1", "X", null, 1L, Barcode.internalEan13(2), null,
                new BigDecimal("-1"), null, 0, null, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void canBeDeactivated() {
        Product product = sample();
        product.deactivate();
        assertThat(product.isActive()).isFalse();
    }
}
