package ma.pfe.stock.supplier.application;

import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.supplier.application.dto.SupplierCommand;
import ma.pfe.stock.supplier.application.service.SupplierService;
import ma.pfe.stock.supplier.domain.model.Supplier;
import ma.pfe.stock.supplier.domain.port.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierServiceTest {

    private SupplierRepository repository;
    private SupplierService service;

    @BeforeEach
    void setUp() {
        repository = mock(SupplierRepository.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new SupplierService(repository);
    }

    private static SupplierCommand command(String code, String taxId) {
        return new SupplierCommand(code, "Atlas", null, null, null, null, "Rabat", "MA", taxId, null);
    }

    @Test
    void createsSupplier() {
        var view = service.create(command("f1", null));
        assertThat(view.code()).isEqualTo("F1");
        assertThat(view.active()).isTrue();
    }

    @Test
    void rejectsDuplicateCode() {
        when(repository.existsByCode("F1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(command("F1", null))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsDuplicateTaxIdInSameCountry() {
        when(repository.existsByCountryAndTaxId("MA", "001234567000089")).thenReturn(true);
        assertThatThrownBy(() -> service.create(command("F2", "001234567000089")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deactivatesSupplier() {
        Supplier s = Supplier.create("F1", "Atlas", null, null, null, null, null, null, null, null);
        when(repository.findById(1L)).thenReturn(Optional.of(s));
        assertThat(service.setActive(1L, false).active()).isFalse();
    }

    @Test
    void unknownSupplierThrowsNotFound() {
        when(repository.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(5L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
