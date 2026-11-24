package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseRecommendationRepository {

    PurchaseRecommendation save(PurchaseRecommendation recommendation);

    Optional<PurchaseRecommendation> findById(Long id);

    PageResult<PurchaseRecommendation> search(RecommendationStatus status, RecommendationType type, int page,
                                              int size);

    List<PurchaseRecommendation> findOpen(Long productId, RecommendationType type);

    List<PurchaseRecommendation> findCreatedSince(Instant since);

    List<PurchaseRecommendation> findOpenNotNotified();
}
