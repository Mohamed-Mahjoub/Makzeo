package ma.pfe.stock.procurement.infrastructure.ai;

import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import ma.pfe.stock.procurement.infrastructure.websearch.QuoteTextParser;
import ma.pfe.stock.procurement.infrastructure.websearch.WebSearchMarketIntelligence;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Choisit le moteur de l'agent selon les clés fournies : l'API Claude si {@code AI_API_KEY} est
 * renseignée, sinon la veille Serper + Tavily si {@code SERPER_API_KEY} l'est. Sans modèle de langage,
 * les demandes de prix utilisent le modèle de texte de l'application et les devis collés sont lus par
 * {@link QuoteTextParser}, ce qui fonctionne même sans aucune clé.
 */
@Component
@Primary
public class MarketIntelligenceRouter implements MarketIntelligence {

    private final ClaudeMarketIntelligence claude;
    private final WebSearchMarketIntelligence web;
    private final ProcurementProperties properties;
    private final ZoneId zone;

    public MarketIntelligenceRouter(ClaudeMarketIntelligence claude, WebSearchMarketIntelligence web,
                                    ProcurementProperties properties, ZoneId businessZoneId) {
        this.claude = claude;
        this.web = web;
        this.properties = properties;
        this.zone = businessZoneId;
    }

    @Override
    public boolean enabled() {
        return claude.enabled() || web.enabled();
    }

    @Override
    public String provider() {
        return claude.enabled() ? claude.provider() : web.enabled() ? web.provider() : null;
    }

    @Override
    public List<SupplierFinding> findSuppliers(ProductInfo product, int maxResults) {
        return claude.enabled() ? claude.findSuppliers(product, maxResults) : web.findSuppliers(product, maxResults);
    }

    @Override
    public Optional<MarketSignal> marketSignal(ProductInfo product, int horizonMonths) {
        return claude.enabled() ? claude.marketSignal(product, horizonMonths) : Optional.empty();
    }

    @Override
    public RfqText writeRfq(RfqRequest request) {
        return claude.writeRfq(request); // sans clé Claude : modèle de texte de l'application
    }

    @Override
    public Optional<ExtractedQuote> extractQuote(String quoteText, ProductInfo product) {
        if (claude.enabled()) {
            return claude.extractQuote(quoteText, product);
        }
        return QuoteTextParser.parse(quoteText, product, properties.currency(), LocalDate.now(zone));
    }
}
