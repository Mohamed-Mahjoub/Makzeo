package ma.pfe.stock.procurement.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourcingLeadJpaRepository extends JpaRepository<SourcingLeadEntity, Long> {

    List<SourcingLeadEntity> findByProductIdOrderByDetectedAtDesc(Long productId);

    List<SourcingLeadEntity> findAllByOrderByDetectedAtDesc(Pageable pageable);
}
