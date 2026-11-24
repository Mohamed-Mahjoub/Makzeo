package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.port.PricePointRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class PricePointRepositoryAdapter implements PricePointRepository {

    private final PricePointJpaRepository jpa;

    public PricePointRepositoryAdapter(PricePointJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PricePoint save(PricePoint point) {
        return jpa.save(PricePointEntity.from(point)).toDomain();
    }

    @Override
    public List<PricePoint> findByProduct(Long productId) {
        return jpa.findByProductIdOrderByObservedOnAscIdAsc(productId).stream().map(PricePointEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<PricePoint> findLatest(Long productId, Collection<PriceSource> sources) {
        return jpa.findFirstByProductIdAndSourceInOrderByObservedOnDescIdDesc(productId, sources)
                .map(PricePointEntity::toDomain);
    }

    @Override
    public Set<Long> productIdsWithSources(Collection<PriceSource> sources) {
        return new HashSet<>(jpa.findDistinctProductIds(sources));
    }
}
