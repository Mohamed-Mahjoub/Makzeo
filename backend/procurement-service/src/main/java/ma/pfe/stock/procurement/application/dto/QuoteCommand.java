package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.QuoteSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Enregistrement d'un devis (saisi, ou lu par l'IA puis vérifié par l'acheteur). */
public record QuoteCommand(Long rfqId, Long productId, Long marketSupplierId, String offeredName,
                           Equivalence equivalence, BigDecimal unitPrice, boolean priceIncludesVat,
                           Integer minQuantity, Availability availability, Integer leadTimeDays, String paymentTerms,
                           LocalDate validUntil, Integer qualityScore, QuoteSource source) {
}
