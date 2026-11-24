package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PriceSource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PricePointRepository {

    PricePoint save(PricePoint point);

    List<PricePoint> findByProduct(Long productId);

    /** Dernier prix de l'une des sources indiquées (ex : dernier prix payé). */
    Optional<PricePoint> findLatest(Long productId, Collection<PriceSource> sources);

    /** Articles ayant au moins un prix de l'une des sources indiquées. */
    Set<Long> productIdsWithSources(Collection<PriceSource> sources);
}
