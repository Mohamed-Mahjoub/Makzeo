package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class StockItemRepositoryAdapter implements StockItemRepository {

    private final StockItemJpaRepository jpa;

    public StockItemRepositoryAdapter(StockItemJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StockItem lockOrCreate(ProductSnapshot product) {
        return jpa.lockByProductId(product.id())
                .or(() -> {
                    jpa.insertIfAbsent(product.id(), product.reference(), product.name(), product.minStock());
                    return jpa.lockByProductId(product.id());
                })
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalStateException("Niveau de stock introuvable : " + product.id()));
    }

    @Override
    public Optional<StockItem> findByProductId(Long productId) {
        return jpa.findByProductId(productId).map(this::toDomain);
    }

    @Override
    public List<StockItem> findByProductIds(Collection<Long> productIds) {
        return jpa.findByProductIdIn(productIds).stream().map(this::toDomain).toList();
    }

    @Override
    public List<StockItem> findAll() {
        return jpa.findAll(Sort.by("productReference")).stream().map(this::toDomain).toList();
    }

    @Override
    public StockItem save(StockItem item) {
        StockItemEntity e = jpa.findByProductId(item.getProductId()).orElseGet(StockItemEntity::new);
        e.setProductId(item.getProductId());
        e.setProductReference(item.getProductReference());
        e.setProductName(item.getProductName());
        e.setMinStock(item.getMinStock());
        e.setQuantity(item.getQuantity());
        e.setLastMovementAt(item.getLastMovementAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public PageResult<StockItem> search(String keyword, StockStatus status, int page, int size) {
        Specification<StockItemEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("productReference")), like),
                        cb.like(cb.lower(root.get("productName")), like)));
            }
            if (status != null) {
                predicates.add(statusPredicate(root, cb, status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<StockItemEntity> result = jpa.findAll(spec, PageRequest.of(page, size, Sort.by("productReference")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long count() {
        return jpa.count();
    }

    @Override
    public long sumQuantity() {
        return jpa.sumQuantity();
    }

    @Override
    public long countByStatus(StockStatus status) {
        return jpa.count((root, query, cb) -> statusPredicate(root, cb, status));
    }

    private static Predicate statusPredicate(jakarta.persistence.criteria.Root<StockItemEntity> root,
                                             jakarta.persistence.criteria.CriteriaBuilder cb, StockStatus status) {
        return switch (status) {
            case OUT_OF_STOCK -> cb.le(root.get("quantity"), 0);
            case LOW -> cb.and(cb.gt(root.get("quantity"), 0),
                    cb.le(root.<Integer>get("quantity"), root.<Integer>get("minStock")));
            case OK -> cb.and(cb.gt(root.get("quantity"), 0),
                    cb.gt(root.<Integer>get("quantity"), root.<Integer>get("minStock")));
        };
    }

    private StockItem toDomain(StockItemEntity e) {
        return new StockItem(e.getId(), e.getProductId(), e.getProductReference(), e.getProductName(),
                e.getMinStock(), e.getQuantity(), e.getLastMovementAt());
    }
}
