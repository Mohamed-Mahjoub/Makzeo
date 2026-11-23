package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.port.MarketSupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class MarketSupplierRepositoryAdapter implements MarketSupplierRepository {

    private final MarketSupplierJpaRepository jpa;

    public MarketSupplierRepositoryAdapter(MarketSupplierJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public MarketSupplier save(MarketSupplier supplier) {
        return jpa.save(MarketSupplierEntity.from(supplier)).toDomain();
    }

    @Override
    public Optional<MarketSupplier> findById(Long id) {
        return jpa.findById(id).map(MarketSupplierEntity::toDomain);
    }

    @Override
    public Optional<MarketSupplier> findByNormalizedKey(String key) {
        return jpa.findByNormalizedKey(key).map(MarketSupplierEntity::toDomain);
    }

    @Override
    public Optional<MarketSupplier> findByCatalogSupplierId(Long catalogSupplierId) {
        return jpa.findFirstByCatalogSupplierId(catalogSupplierId).map(MarketSupplierEntity::toDomain);
    }

    @Override
    public List<MarketSupplier> findAllByIds(Collection<Long> ids) {
        return jpa.findAllById(ids).stream().map(MarketSupplierEntity::toDomain).toList();
    }

    @Override
    public PageResult<MarketSupplier> search(MarketSupplierStatus status, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "discoveredAt"));
        Page<MarketSupplierEntity> result = status == null ? jpa.findAll(request) : jpa.findByStatus(status, request);
        return PageResult.of(result.getContent().stream().map(MarketSupplierEntity::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long countByStatus(MarketSupplierStatus status) {
        return jpa.countByStatus(status);
    }
}
