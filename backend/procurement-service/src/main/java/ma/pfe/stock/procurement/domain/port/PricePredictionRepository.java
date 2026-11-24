package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.procurement.domain.model.PricePrediction;

import java.util.List;
import java.util.Optional;

public interface PricePredictionRepository {

    /** Remplace la prédiction précédente de l'article. */
    PricePrediction upsert(PricePrediction prediction);

    Optional<PricePrediction> findByProduct(Long productId);

    List<PricePrediction> findAll();
}
