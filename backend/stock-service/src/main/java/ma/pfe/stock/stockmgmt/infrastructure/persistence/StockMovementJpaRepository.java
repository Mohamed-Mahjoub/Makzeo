package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long>,
        JpaSpecificationExecutor<StockMovementEntity> {

    long countByTypeAndOccurredAtGreaterThanEqual(MovementType type, Instant since);
}
