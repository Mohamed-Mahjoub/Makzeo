package ma.pfe.stock.procurement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricePredictionJpaRepository extends JpaRepository<PricePredictionEntity, Long> {

    Optional<PricePredictionEntity> findByProductId(Long productId);
}
