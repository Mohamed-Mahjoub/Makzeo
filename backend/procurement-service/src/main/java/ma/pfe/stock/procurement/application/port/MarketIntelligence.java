package ma.pfe.stock.procurement.application.port;

import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.ProductInfo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Agent achats : recherche de fournisseurs sur le marché, tendance des prix, rédaction des demandes de prix et
 * lecture des devis. Toutes les réponses sont des propositions : les prix et coordonnées sont vérifiés
 * par l'application et validés par un acheteur avant usage.
 */
public interface MarketIntelligence {

    boolean enabled();

    /** Nom du moteur utilisé pour la veille web (affiché dans l'application et le rapport). */
    default String provider() {
        return enabled() ? "Agent IA" : null;
    }

    /** Fournisseurs du marché proposant l'article ou un équivalent. */
    List<SupplierFinding> findSuppliers(ProductInfo product, int maxResults);

    /** Tendance attendue du prix de l'article sur l'horizon (matières premières, annonces, marché). */
    Optional<MarketSignal> marketSignal(ProductInfo product, int horizonMonths);

    /** Texte d'une demande de prix. */
    RfqText writeRfq(RfqRequest request);

    /** Lecture d'un devis collé par l'acheteur (courriel, texte d'un PDF). */
    Optional<ExtractedQuote> extractQuote(String quoteText, ProductInfo product);

    record SupplierFinding(String name, String website, String email, String phone, String city,
                           String offeredName, String offeredReference, String url, BigDecimal price,
                           String currency, boolean priceIncludesVat, Equivalence equivalence, int similarity,
                           String rationale) {
    }

    record MarketSignal(double expectedChangePct, String rationale, List<String> sources) {
    }

    record RfqRequest(ProductInfo product, String supplierName, int quantity, LocalDate responseDueDate,
                      String buyerCompany, String buyerEmail) {
    }

    record RfqText(String subject, String body) {
    }

    record ExtractedQuote(String supplierName, String offeredName, BigDecimal unitPrice, String currency,
                          boolean priceIncludesVat, Integer minQuantity, Availability availability,
                          Integer leadTimeDays, String paymentTerms, LocalDate validUntil, String notes) {
    }
}
