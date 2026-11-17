package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.StockAlert;
import ma.pfe.stock.stockmgmt.domain.port.StockAlertRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class StockAlertRepositoryAdapter implements StockAlertRepository {

    private final StockAlertJpaRepository jpa;

    public StockAlertRepositoryAdapter(StockAlertJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StockAlert save(StockAlert a) {
        StockAlertEntity e = a.getId() == null ? new StockAlertEntity()
                : jpa.findById(a.getId()).orElseGet(StockAlertEntity::new);
        e.setProductId(a.getProductId());
        e.setProductReference(a.getProductReference());
        e.setProductName(a.getProductName());
        e.setType(a.getType());
        e.setStatus(a.getStatus());
        e.setQuantity(a.getQuantity());
        e.setThreshold(a.getThreshold());
        e.setCreatedAt(a.getCreatedAt());
        e.setAcknowledgedBy(a.getAcknowledgedBy());
        e.setAcknowledgedAt(a.getAcknowledgedAt());
        e.setResolvedAt(a.getResolvedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<StockAlert> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<StockAlert> findActiveByProductId(Long productId) {
        return jpa.findByProductIdAndStatusNot(productId, AlertStatus.RESOLVED).stream().map(this::toDomain).toList();
    }

    @Override
    public PageResult<StockAlert> search(AlertStatus status, AlertType type, Boolean activeOnly, int page, int size) {
        Specification<StockAlertEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                p.add(cb.equal(root.get("type"), type));
            }
            if (Boolean.TRUE.equals(activeOnly)) {
                p.add(cb.notEqual(root.get("status"), AlertStatus.RESOLVED));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<StockAlertEntity> result = jpa.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long countActive(AlertType type) {
        return jpa.countByTypeAndStatusNot(type, AlertStatus.RESOLVED);
    }

    private StockAlert toDomain(StockAlertEntity e) {
        return new StockAlert(e.getId(), e.getProductId(), e.getProductReference(), e.getProductName(), e.getType(),
                e.getStatus(), e.getQuantity(), e.getThreshold(), e.getCreatedAt(), e.getAcknowledgedBy(),
                e.getAcknowledgedAt(), e.getResolvedAt());
    }
}
