package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.procurement.domain.model.Quote;

import java.util.List;
import java.util.Optional;

public interface QuoteRepository {

    Quote save(Quote quote);

    Optional<Quote> findById(Long id);

    List<Quote> findByProduct(Long productId);
}
