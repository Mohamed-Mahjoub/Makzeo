package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketSupplierRepository {

    MarketSupplier save(MarketSupplier supplier);

    Optional<MarketSupplier> findById(Long id);

    Optional<MarketSupplier> findByNormalizedKey(String key);

    Optional<MarketSupplier> findByCatalogSupplierId(Long catalogSupplierId);

    List<MarketSupplier> findAllByIds(Collection<Long> ids);

    PageResult<MarketSupplier> search(MarketSupplierStatus status, int page, int size);

    long countByStatus(MarketSupplierStatus status);
}
