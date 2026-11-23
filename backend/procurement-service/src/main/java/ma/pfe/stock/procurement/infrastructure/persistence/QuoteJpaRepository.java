package ma.pfe.stock.procurement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, Long> {

    List<QuoteEntity> findByProductIdOrderByReceivedAtDesc(Long productId);
}
