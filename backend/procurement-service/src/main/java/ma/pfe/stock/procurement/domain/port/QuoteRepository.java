package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.procurement.domain.model.Quote;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QuoteRepository {

    Quote save(Quote quote);

    Optional<Quote> findById(Long id);

    /** Articles ayant reçu au moins un devis. */
    Set<Long> productIds();

    List<Quote> findByProduct(Long productId);
}
