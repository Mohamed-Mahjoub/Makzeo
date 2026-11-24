package ma.pfe.stock.procurement.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Article ayant au moins une offre : navigation rapide de la page de comparaison. */
public record QuotedProductView(Long productId, String reference, String name, int offers, BigDecimal bestUnitPrice,
                                BigDecimal referencePrice, Instant lastOfferAt) {
}
