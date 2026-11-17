package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.Inventory;
import ma.pfe.stock.stockmgmt.domain.model.InventoryLine;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;
import ma.pfe.stock.stockmgmt.domain.port.InventoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final InventoryJpaRepository jpa;

    public InventoryRepositoryAdapter(InventoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Inventory save(Inventory inv) {
        InventoryEntity e = inv.getId() == null ? new InventoryEntity()
                : jpa.findById(inv.getId()).orElseGet(InventoryEntity::new);
        e.setName(inv.getName());
        e.setNotes(inv.getNotes());
        e.setStatus(inv.getStatus());
        e.setCreatedBy(inv.getCreatedBy());
        e.setCreatedAt(inv.getCreatedAt());
        e.setClosedBy(inv.getClosedBy());
        e.setClosedAt(inv.getClosedAt());
        e.getLines().clear();
        for (InventoryLine l : inv.getLines()) {
            InventoryLineEmbeddable le = new InventoryLineEmbeddable();
            le.setProductId(l.productId());
            le.setProductReference(l.productReference());
            le.setProductName(l.productName());
            le.setExpectedQuantity(l.expectedQuantity());
            le.setCountedQuantity(l.countedQuantity());
            le.setCountedBy(l.countedBy());
            le.setCountedAt(l.countedAt());
            e.getLines().add(le);
        }
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Inventory> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public PageResult<Inventory> search(InventoryStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<InventoryEntity> result = status == null ? jpa.findAll(pageable) : jpa.findByStatus(status, pageable);
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    private Inventory toDomain(InventoryEntity e) {
        return new Inventory(e.getId(), e.getName(), e.getNotes(), e.getStatus(), e.getCreatedBy(), e.getCreatedAt(),
                e.getClosedBy(), e.getClosedAt(), e.getLines().stream()
                .map(l -> new InventoryLine(l.getProductId(), l.getProductReference(), l.getProductName(),
                        l.getExpectedQuantity(), l.getCountedQuantity(), l.getCountedBy(), l.getCountedAt()))
                .toList());
    }
}
