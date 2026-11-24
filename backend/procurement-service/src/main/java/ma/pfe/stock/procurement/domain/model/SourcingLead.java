package ma.pfe.stock.procurement.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Opportunité détectée par la veille : un fournisseur propose l'article du catalogue ou un équivalent.
 *
 * @param similarity proximité technique estimée avec l'article du catalogue (0 à 100)
 */
public record SourcingLead(Long id, Long productId, Long marketSupplierId, String supplierName, String offeredName,
                           String offeredReference, String url, BigDecimal publishedPrice, String currency,
                           Equivalence equivalence, int similarity, String rationale, Long watchRunId,
                           Instant detectedAt) {

    public SourcingLead {
        if (offeredName == null || offeredName.isBlank()) {
            throw new IllegalArgumentException("La désignation de l'article proposé est obligatoire");
        }
        if (similarity < 0 || similarity > 100) {
            throw new IllegalArgumentException("La similarité doit être comprise entre 0 et 100");
        }
        if (publishedPrice != null && publishedPrice.signum() <= 0) {
            publishedPrice = null;
        }
        detectedAt = detectedAt != null ? detectedAt : Instant.now();
    }
}
