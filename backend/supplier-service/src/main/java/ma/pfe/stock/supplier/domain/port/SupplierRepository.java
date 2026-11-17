package ma.pfe.stock.supplier.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.supplier.domain.model.Supplier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SupplierRepository {

    Supplier save(Supplier supplier);

    Optional<Supplier> findById(Long id);

    List<Supplier> findAllByIds(Collection<Long> ids);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    /** Unicité de l'identifiant fiscal au sein d'un même pays. */
    boolean existsByCountryAndTaxId(String country, String taxId);

    boolean existsByCountryAndTaxIdAndIdNot(String country, String taxId, Long id);

    PageResult<Supplier> search(String keyword, Boolean active, String country, int page, int size);
}
