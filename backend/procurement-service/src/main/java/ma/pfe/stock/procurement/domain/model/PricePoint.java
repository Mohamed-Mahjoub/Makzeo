package ma.pfe.stock.procurement.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Observation d'un prix unitaire à une date. */
public record PricePoint(Long id, Long productId, BigDecimal unitPrice, LocalDate observedOn, PriceSource source,
                         String supplierName, Integer quantity) {

    public PricePoint {
        if (productId == null) {
            throw new IllegalArgumentException("L'article est obligatoire");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Le prix doit être strictement positif");
        }
        if (observedOn == null) {
            throw new IllegalArgumentException("La date du prix est obligatoire");
        }
        if (quantity != null && quantity <= 0) {
            throw new IllegalArgumentException("La quantité achetée doit être positive");
        }
    }
}
