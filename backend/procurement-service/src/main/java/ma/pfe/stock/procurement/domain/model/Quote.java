package ma.pfe.stock.procurement.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Offre de prix d'un fournisseur pour un article (ou un équivalent).
 *
 * @param leadTimeDays délai de livraison annoncé, en jours (null si inconnu)
 * @param qualityScore appréciation de la qualité de 1 à 5 (marque, garantie, historique du fournisseur)
 */
public record Quote(Long id, Long rfqId, Long productId, Long marketSupplierId, String supplierName,
                    String offeredName, Equivalence equivalence, BigDecimal unitPrice, String currency,
                    int minQuantity, Availability availability, Integer leadTimeDays, String paymentTerms,
                    LocalDate validUntil, int qualityScore, QuoteSource source, Instant receivedAt) {

    public Quote {
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("Le prix unitaire doit être strictement positif");
        }
        if (minQuantity < 1) {
            throw new IllegalArgumentException("La quantité minimale de commande doit être au moins 1");
        }
        if (leadTimeDays != null && leadTimeDays < 0) {
            throw new IllegalArgumentException("Le délai de livraison ne peut pas être négatif");
        }
        if (qualityScore < 1 || qualityScore > 5) {
            throw new IllegalArgumentException("La note de qualité doit être comprise entre 1 et 5");
        }
        if (offeredName == null || offeredName.isBlank()) {
            throw new IllegalArgumentException("La désignation de l'article proposé est obligatoire");
        }
        currency = currency == null || currency.isBlank() ? "TND" : currency.trim().toUpperCase();
        equivalence = equivalence == null ? Equivalence.IDENTICAL : equivalence;
        availability = availability == null ? Availability.IN_STOCK : availability;
        receivedAt = receivedAt != null ? receivedAt : Instant.now();
    }

    /** Une offre sans date de validité est considérée valable 30 jours après sa réception. */
    public boolean isValidOn(LocalDate day, java.time.ZoneId zone) {
        LocalDate limit = validUntil != null ? validUntil : receivedAt.atZone(zone).toLocalDate().plusDays(30);
        return !day.isAfter(limit);
    }
}
