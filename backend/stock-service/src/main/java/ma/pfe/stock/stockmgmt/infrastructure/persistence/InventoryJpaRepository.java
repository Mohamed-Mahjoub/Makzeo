package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryJpaRepository extends JpaRepository<InventoryEntity, Long> {

    Page<InventoryEntity> findByStatus(InventoryStatus status, Pageable pageable);
}
