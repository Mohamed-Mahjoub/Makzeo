package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
import ma.pfe.stock.procurement.domain.port.PurchaseRecommendationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class PurchaseRecommendationRepositoryAdapter implements PurchaseRecommendationRepository {

    private final PurchaseRecommendationJpaRepository jpa;

    public PurchaseRecommendationRepositoryAdapter(PurchaseRecommendationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PurchaseRecommendation save(PurchaseRecommendation recommendation) {
        return jpa.save(PurchaseRecommendationEntity.from(recommendation)).toDomain();
    }

    @Override
    public Optional<PurchaseRecommendation> findById(Long id) {
        return jpa.findById(id).map(PurchaseRecommendationEntity::toDomain);
    }

    @Override
    public PageResult<PurchaseRecommendation> search(RecommendationStatus status, RecommendationType type, int page,
                                                     int size) {
        PageRequest request = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "netSavings").and(Sort.by(Sort.Direction.DESC, "createdAt")));
        Page<PurchaseRecommendationEntity> result;
        if (status != null && type != null) {
            result = jpa.findByStatusAndType(status, type, request);
        } else if (status != null) {
            result = jpa.findByStatus(status, request);
        } else if (type != null) {
            result = jpa.findByType(type, request);
        } else {
            result = jpa.findAll(request);
        }
        return PageResult.of(result.getContent().stream().map(PurchaseRecommendationEntity::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    @Override
    public List<PurchaseRecommendation> findOpen(Long productId, RecommendationType type) {
        return jpa.findByProductIdAndTypeAndStatus(productId, type, RecommendationStatus.OPEN).stream()
                .map(PurchaseRecommendationEntity::toDomain).toList();
    }

    @Override
    public List<PurchaseRecommendation> findCreatedSince(Instant since) {
        return jpa.findByCreatedAtGreaterThanEqual(since).stream().map(PurchaseRecommendationEntity::toDomain)
                .toList();
    }

    @Override
    public List<PurchaseRecommendation> findOpenNotNotified() {
        return jpa.findByStatusAndNotifiedAtIsNull(RecommendationStatus.OPEN).stream()
                .map(PurchaseRecommendationEntity::toDomain).toList();
    }
}
