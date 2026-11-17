package ma.pfe.stock.stockmgmt.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.StockAlert;

import java.util.List;
import java.util.Optional;

public interface StockAlertRepository {

    StockAlert save(StockAlert alert);

    Optional<StockAlert> findById(Long id);

    /** Alertes non résolues (OPEN ou ACKNOWLEDGED) d'un produit. */
    List<StockAlert> findActiveByProductId(Long productId);

    PageResult<StockAlert> search(AlertStatus status, AlertType type, Boolean activeOnly, int page, int size);

    long countActive(AlertType type);
}
