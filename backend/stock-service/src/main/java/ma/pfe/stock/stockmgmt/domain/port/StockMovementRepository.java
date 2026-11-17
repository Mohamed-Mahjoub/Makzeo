package ma.pfe.stock.stockmgmt.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.StockMovement;

import java.time.Instant;

public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    PageResult<StockMovement> search(MovementCriteria criteria, int page, int size);

    long countSince(Instant since, MovementType type);

    record MovementCriteria(Long productId, MovementType type, Instant from, Instant to, String performedBy,
                            Long supplierId, Long inventoryId) {
    }
}
