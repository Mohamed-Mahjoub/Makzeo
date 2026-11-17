package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface StockAlertJpaRepository extends JpaRepository<StockAlertEntity, Long>,
        JpaSpecificationExecutor<StockAlertEntity> {

    List<StockAlertEntity> findByProductIdAndStatusNot(Long productId, AlertStatus status);

    long countByTypeAndStatusNot(AlertType type, AlertStatus status);
}
