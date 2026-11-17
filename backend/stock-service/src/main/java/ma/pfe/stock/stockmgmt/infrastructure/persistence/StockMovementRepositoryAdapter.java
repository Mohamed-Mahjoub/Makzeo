package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.StockMovement;
import ma.pfe.stock.stockmgmt.domain.port.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final StockMovementJpaRepository jpa;

    public StockMovementRepositoryAdapter(StockMovementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StockMovement save(StockMovement m) {
        StockMovementEntity e = new StockMovementEntity();
        e.setProductId(m.productId());
        e.setProductReference(m.productReference());
        e.setProductName(m.productName());
        e.setType(m.type());
        e.setQuantity(m.quantity());
        e.setQuantityBefore(m.quantityBefore());
        e.setQuantityAfter(m.quantityAfter());
        e.setSupplierId(m.supplierId());
        e.setDocumentReference(m.documentReference());
        e.setReason(m.reason());
        e.setInventoryId(m.inventoryId());
        e.setPerformedBy(m.performedBy());
        e.setOccurredAt(m.occurredAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public PageResult<StockMovement> search(MovementCriteria c, int page, int size) {
        Specification<StockMovementEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (c.productId() != null) {
                p.add(cb.equal(root.get("productId"), c.productId()));
            }
            if (c.type() != null) {
                p.add(cb.equal(root.get("type"), c.type()));
            }
            if (c.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), c.from()));
            }
            if (c.to() != null) {
                p.add(cb.lessThan(root.get("occurredAt"), c.to()));
            }
            if (c.performedBy() != null && !c.performedBy().isBlank()) {
                p.add(cb.equal(root.get("performedBy"), c.performedBy().trim().toLowerCase()));
            }
            if (c.supplierId() != null) {
                p.add(cb.equal(root.get("supplierId"), c.supplierId()));
            }
            if (c.inventoryId() != null) {
                p.add(cb.equal(root.get("inventoryId"), c.inventoryId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<StockMovementEntity> result = jpa.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long countSince(Instant since, MovementType type) {
        return jpa.countByTypeAndOccurredAtGreaterThanEqual(type, since);
    }

    private StockMovement toDomain(StockMovementEntity e) {
        return new StockMovement(e.getId(), e.getProductId(), e.getProductReference(), e.getProductName(),
                e.getType(), e.getQuantity(), e.getQuantityBefore(), e.getQuantityAfter(), e.getSupplierId(),
                e.getDocumentReference(), e.getReason(), e.getInventoryId(), e.getPerformedBy(), e.getOccurredAt());
    }
}
