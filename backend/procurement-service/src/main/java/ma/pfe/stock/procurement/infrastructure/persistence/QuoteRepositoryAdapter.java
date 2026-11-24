package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.Quote;
import ma.pfe.stock.procurement.domain.port.QuoteRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class QuoteRepositoryAdapter implements QuoteRepository {

    private final QuoteJpaRepository jpa;

    public QuoteRepositoryAdapter(QuoteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Quote save(Quote quote) {
        return jpa.save(QuoteEntity.from(quote)).toDomain();
    }

    @Override
    public Optional<Quote> findById(Long id) {
        return jpa.findById(id).map(QuoteEntity::toDomain);
    }

    @Override
    public List<Quote> findByProduct(Long productId) {
        return jpa.findByProductIdOrderByReceivedAtDesc(productId).stream().map(QuoteEntity::toDomain).toList();
    }

    @Override
    public Set<Long> productIds() {
        return new HashSet<>(jpa.findDistinctProductIds());
    }
}
