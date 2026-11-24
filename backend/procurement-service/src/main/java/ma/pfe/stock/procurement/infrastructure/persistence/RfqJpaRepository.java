package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.RfqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface RfqJpaRepository extends JpaRepository<RfqEntity, Long> {

    Page<RfqEntity> findByStatus(RfqStatus status, Pageable pageable);

    Page<RfqEntity> findByProductId(Long productId, Pageable pageable);

    Page<RfqEntity> findByStatusAndProductId(RfqStatus status, Long productId, Pageable pageable);

    boolean existsByProductIdAndMarketSupplierIdAndStatusIn(Long productId, Long marketSupplierId,
                                                           Collection<RfqStatus> statuses);

    long countByStatus(RfqStatus status);
}
