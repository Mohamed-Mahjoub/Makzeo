package ma.pfe.stock.stockmgmt.application;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockAlert;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockMovement;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;
import ma.pfe.stock.stockmgmt.domain.port.StockAlertRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockMovementRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Implémentations en mémoire des ports (tests de la couche application sans base de données). */
final class InMemoryStockFixtures {

    private InMemoryStockFixtures() {
    }

    static final class Items implements StockItemRepository {
        final Map<Long, StockItem> byProduct = new HashMap<>();

        public StockItem lockOrCreate(ProductSnapshot p) { return byProduct.computeIfAbsent(p.id(), id -> StockItem.empty(p)); }
        public Optional<StockItem> findByProductId(Long productId) { return Optional.ofNullable(byProduct.get(productId)); }
        public List<StockItem> findByProductIds(Collection<Long> ids) { return ids.stream().map(byProduct::get).filter(java.util.Objects::nonNull).toList(); }
        public List<StockItem> findAll() { return new ArrayList<>(byProduct.values()); }
        public StockItem save(StockItem item) { byProduct.put(item.getProductId(), item); return item; }
        public PageResult<StockItem> search(String k, StockStatus s, int p, int size) { return PageResult.of(findAll(), p, size, byProduct.size()); }
        public long count() { return byProduct.size(); }
        public long sumQuantity() { return byProduct.values().stream().mapToLong(StockItem::getQuantity).sum(); }
        public long countByStatus(StockStatus status) { return byProduct.values().stream().filter(i -> i.status() == status).count(); }
    }

    static final class Movements implements StockMovementRepository {
        final List<StockMovement> saved = new ArrayList<>();

        public StockMovement save(StockMovement m) { saved.add(m); return m; }
        public PageResult<StockMovement> search(MovementCriteria c, int p, int s) { return PageResult.of(saved, p, s, saved.size()); }
        public long countSince(Instant since, MovementType type) { return saved.stream().filter(m -> m.type() == type).count(); }
    }

    static final class Alerts implements StockAlertRepository {
        final Map<Long, StockAlert> byId = new HashMap<>();
        private long sequence = 1;

        public StockAlert save(StockAlert a) {
            StockAlert stored = a.getId() != null ? a : new StockAlert(sequence++, a.getProductId(), a.getProductReference(),
                    a.getProductName(), a.getType(), a.getStatus(), a.getQuantity(), a.getThreshold(), a.getCreatedAt(),
                    a.getAcknowledgedBy(), a.getAcknowledgedAt(), a.getResolvedAt());
            byId.put(stored.getId(), stored);
            return stored;
        }
        public Optional<StockAlert> findById(Long id) { return Optional.ofNullable(byId.get(id)); }
        public List<StockAlert> findActiveByProductId(Long productId) { return byId.values().stream().filter(a -> a.getProductId().equals(productId) && a.isActive()).toList(); }
        public PageResult<StockAlert> search(AlertStatus s, AlertType t, Boolean a, int p, int size) { return PageResult.of(new ArrayList<>(byId.values()), p, size, byId.size()); }
        public long countActive(AlertType type) { return byId.values().stream().filter(a -> a.getType() == type && a.isActive()).count(); }

        List<StockAlert> active() { return byId.values().stream().filter(StockAlert::isActive).toList(); }
    }
}
