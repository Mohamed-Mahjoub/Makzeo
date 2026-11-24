package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.port.PricePredictionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PricePredictionRepositoryAdapter implements PricePredictionRepository {

    private final PricePredictionJpaRepository jpa;

    public PricePredictionRepositoryAdapter(PricePredictionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PricePrediction upsert(PricePrediction prediction) {
        PricePredictionEntity entity = jpa.findByProductId(prediction.productId())
                .orElseGet(PricePredictionEntity::create);
        entity.copyFrom(prediction);
        return jpa.save(entity).toDomain();
    }

    @Override
    public Optional<PricePrediction> findByProduct(Long productId) {
        return jpa.findByProductId(productId).map(PricePredictionEntity::toDomain);
    }

    @Override
    public List<PricePrediction> findAll() {
        return jpa.findAll().stream().map(PricePredictionEntity::toDomain).toList();
    }
}
