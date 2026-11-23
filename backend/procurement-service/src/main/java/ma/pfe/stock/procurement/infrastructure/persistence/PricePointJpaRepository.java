package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PricePointJpaRepository extends JpaRepository<PricePointEntity, Long> {

    List<PricePointEntity> findByProductIdOrderByObservedOnAscIdAsc(Long productId);

    Optional<PricePointEntity> findFirstByProductIdAndSourceInOrderByObservedOnDescIdDesc(Long productId,
                                                                                       Collection<PriceSource> sources);
}
