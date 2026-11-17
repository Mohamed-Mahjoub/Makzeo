package ma.pfe.stock.stockmgmt.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.Inventory;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;

import java.util.Optional;

public interface InventoryRepository {

    Inventory save(Inventory inventory);

    Optional<Inventory> findById(Long id);

    PageResult<Inventory> search(InventoryStatus status, int page, int size);
}
