package ma.pfe.stock.procurement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, Long> {

    List<QuoteEntity> findByProductIdOrderByReceivedAtDesc(Long productId);

    @Query("select distinct q.productId from QuoteEntity q")
    List<Long> findDistinctProductIds();
}
