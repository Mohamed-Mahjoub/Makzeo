package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PurchaseRecommendationJpaRepository extends JpaRepository<PurchaseRecommendationEntity, Long> {

    Page<PurchaseRecommendationEntity> findByStatus(RecommendationStatus status, Pageable pageable);

    Page<PurchaseRecommendationEntity> findByType(RecommendationType type, Pageable pageable);

    Page<PurchaseRecommendationEntity> findByStatusAndType(RecommendationStatus status, RecommendationType type,
                                                           Pageable pageable);

    List<PurchaseRecommendationEntity> findByProductIdAndTypeAndStatus(Long productId, RecommendationType type,
                                                                       RecommendationStatus status);

    List<PurchaseRecommendationEntity> findByCreatedAtGreaterThanEqual(Instant since);

    List<PurchaseRecommendationEntity> findByStatusAndNotifiedAtIsNull(RecommendationStatus status);
}
