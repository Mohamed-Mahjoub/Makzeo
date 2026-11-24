package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.PriceSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PricePointJpaRepository extends JpaRepository<PricePointEntity, Long> {

    List<PricePointEntity> findByProductIdOrderByObservedOnAscIdAsc(Long productId);

    @Query("select distinct p.productId from PricePointEntity p where p.source in :sources")
    List<Long> findDistinctProductIds(@Param("sources") Collection<PriceSource> sources);

    Optional<PricePointEntity> findFirstByProductIdAndSourceInOrderByObservedOnDescIdDesc(Long productId,
                                                                                       Collection<PriceSource> sources);
}
