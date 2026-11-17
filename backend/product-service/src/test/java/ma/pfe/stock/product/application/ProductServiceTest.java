package ma.pfe.stock.product.application;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.product.application.dto.ProductCommand;
import ma.pfe.stock.product.application.dto.ProductView;
import ma.pfe.stock.product.application.port.BarcodeSequence;
import ma.pfe.stock.product.application.port.SupplierDirectory;
import ma.pfe.stock.product.application.service.ProductService;
import ma.pfe.stock.product.domain.model.Category;
import ma.pfe.stock.product.domain.port.CategoryRepository;
import ma.pfe.stock.product.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private SupplierDirectory supplierDirectory;
    private ProductService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        BarcodeSequence sequence = mock(BarcodeSequence.class);
        supplierDirectory = mock(SupplierDirectory.class);
        when(sequence.next()).thenReturn(7L);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(new Category(1L, "Informatique", null)));
        service = new ProductService(productRepository, categoryRepository, sequence, supplierDirectory);
    }

    private static ProductCommand command(String barcode, Long supplierId) {
        return new ProductCommand("prd-1", "Souris", null, 1L, barcode, null, new BigDecimal("50"),
                new BigDecimal("80"), 3, null, supplierId);
    }

    @Test
    void generatesInternalEan13WhenNoBarcodeGiven() {
        ProductView view = service.create(command(null, null));

        assertThat(view.barcode()).isEqualTo("2000000000077");
        assertThat(view.categoryName()).isEqualTo("Informatique");
        assertThat(view.reference()).isEqualTo("PRD-1");
    }

    @Test
    void rejectsDuplicateReference() {
        when(productRepository.existsByReference("PRD-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(null, null))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsDuplicateBarcode() {
        when(productRepository.existsByBarcode("4006381333931")).thenReturn(true);

        assertThatThrownBy(() -> service.create(command("4006381333931", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsUnknownSupplier() {
        when(supplierDirectory.exists(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(command(null, 99L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Fournisseur introuvable");
    }

    @Test
    void rejectsUnknownCategory() {
        ProductCommand cmd = new ProductCommand("P2", "X", null, 5L, null, null, null, null, 0, null, null);

        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(ResourceNotFoundException.class);
    }
}
