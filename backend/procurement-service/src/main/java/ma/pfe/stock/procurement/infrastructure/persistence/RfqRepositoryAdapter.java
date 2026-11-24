package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import ma.pfe.stock.procurement.domain.port.RfqRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

@Component
public class RfqRepositoryAdapter implements RfqRepository {

    private final RfqJpaRepository jpa;

    public RfqRepositoryAdapter(RfqJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Rfq save(Rfq rfq) {
        return jpa.save(RfqEntity.from(rfq)).toDomain();
    }

    @Override
    public Optional<Rfq> findById(Long id) {
        return jpa.findById(id).map(RfqEntity::toDomain);
    }

    @Override
    public PageResult<Rfq> search(RfqStatus status, Long productId, int page, int size) {
        PageRequest request = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RfqEntity> result;
        if (status != null && productId != null) {
            result = jpa.findByStatusAndProductId(status, productId, request);
        } else if (status != null) {
            result = jpa.findByStatus(status, request);
        } else if (productId != null) {
            result = jpa.findByProductId(productId, request);
        } else {
            result = jpa.findAll(request);
        }
        return PageResult.of(result.getContent().stream().map(RfqEntity::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public boolean existsOpen(Long productId, Long marketSupplierId, Collection<RfqStatus> openStatuses) {
        return jpa.existsByProductIdAndMarketSupplierIdAndStatusIn(productId, marketSupplierId, openStatuses);
    }

    @Override
    public long countByStatus(RfqStatus status) {
        return jpa.countByStatus(status);
    }
}
