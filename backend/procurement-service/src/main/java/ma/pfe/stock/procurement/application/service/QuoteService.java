package ma.pfe.stock.procurement.application.service;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.procurement.application.dto.ComparisonView;
import ma.pfe.stock.procurement.application.dto.QuoteCommand;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.application.port.StockGateway;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.domain.model.Quote;
import ma.pfe.stock.procurement.domain.model.QuoteSource;
import ma.pfe.stock.procurement.domain.model.StockInfo;
import ma.pfe.stock.procurement.domain.port.QuoteRepository;
import ma.pfe.stock.procurement.domain.service.OfferComparator;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** Collecte des devis et comparaison des offres. */
@Service
public class QuoteService {

    private final QuoteRepository quotes;
    private final MarketSupplierService suppliers;
    private final RfqService rfqs;
    private final PriceService prices;
    private final CatalogGateway catalog;
    private final StockGateway stock;
    private final MarketIntelligence intelligence;
    private final ProcurementProperties properties;
    private final ZoneId zone;

    public QuoteService(QuoteRepository quotes, MarketSupplierService suppliers, RfqService rfqs, PriceService prices,
                        CatalogGateway catalog, StockGateway stock, MarketIntelligence intelligence,
                        ProcurementProperties properties, ZoneId zone) {
        this.quotes = quotes;
        this.suppliers = suppliers;
        this.rfqs = rfqs;
        this.prices = prices;
        this.catalog = catalog;
        this.stock = stock;
        this.intelligence = intelligence;
        this.properties = properties;
        this.zone = zone;
    }

    public List<Quote> findByProduct(Long productId) {
        return quotes.findByProduct(productId);
    }

    /** Enregistre un devis : il alimente aussi l'historique des prix et clôt la demande de prix liée. */
    public Quote register(QuoteCommand cmd) {
        MarketSupplier supplier = suppliers.get(cmd.marketSupplierId());
        if (catalog.product(cmd.productId()).isEmpty()) {
            throw new ResourceNotFoundException("Produit", cmd.productId());
        }
        BigDecimal unitPrice = withoutVat(cmd.unitPrice(), cmd.priceIncludesVat());
        Quote quote = new Quote(null, cmd.rfqId(), cmd.productId(), supplier.getId(), supplier.getName(),
                cmd.offeredName(), cmd.equivalence(), unitPrice, properties.currency(),
                cmd.minQuantity() == null ? 1 : cmd.minQuantity(), cmd.availability(), cmd.leadTimeDays(),
                cmd.paymentTerms(), cmd.validUntil(), cmd.qualityScore() == null ? 3 : cmd.qualityScore(),
                cmd.source() == null ? QuoteSource.MANUAL : cmd.source(), Instant.now());
        Quote saved = quotes.save(quote);
        prices.record(cmd.productId(), unitPrice, LocalDate.now(zone), PriceSource.QUOTE, supplier.getName(),
                null);
        if (cmd.rfqId() != null) {
            rfqs.markAnswered(cmd.rfqId());
        }
        return saved;
    }

    /** Offre relevée sur le site d'un fournisseur par la veille. */
    public Quote registerWebOffer(ProductInfo product, MarketSupplier supplier, MarketIntelligence.SupplierFinding f) {
        BigDecimal unitPrice = withoutVat(f.price(), f.priceIncludesVat());
        return quotes.save(new Quote(null, null, product.id(), supplier.getId(), supplier.getName(),
                f.offeredName(), f.equivalence(), unitPrice, properties.currency(), 1, null, null, null,
                LocalDate.now(zone).plusDays(30), 3, QuoteSource.WEB, Instant.now()));
    }

    /** Lecture d'un devis collé (courriel, texte d'un PDF) : proposition à vérifier avant enregistrement. */
    public MarketIntelligence.ExtractedQuote extract(Long productId, String text) {
        if (!intelligence.enabled()) {
            throw new BusinessRuleException("AI_DISABLED",
                    "L'agent IA n'est pas configuré (clé AI_API_KEY) : saisissez le devis manuellement");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Collez le texte du devis");
        }
        ProductInfo product = catalog.product(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));
        return intelligence.extractQuote(text, product)
                .orElseThrow(() -> new BusinessRuleException("QUOTE_NOT_UNDERSTOOD",
                        "Aucun prix unitaire n'a été trouvé dans ce texte"));
    }

    /** Compare les offres d'un article pour une quantité donnée (par défaut : trois mois de consommation). */
    public ComparisonView compare(Long productId, Integer quantity) {
        ProductInfo product = catalog.product(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));
        double monthly = consumption(productId);
        int needed = quantity != null && quantity > 0 ? quantity
                : (int) Math.max(1, Math.max(Math.ceil(monthly * 3), product.minStock()));
        BigDecimal reference = prices.referencePrice(product);
        List<OfferComparator.RankedOffer> ranked = OfferComparator.compare(quotes.findByProduct(productId), needed,
                reference, LocalDate.now(zone), zone, OfferComparator.Weights.DEFAULT);
        int annualQuantity = (int) Math.round(monthly * 12);
        List<ComparisonView.Offer> offers = ranked.stream().map(r -> {
            Quote q = r.quote();
            BigDecimal annual = reference == null ? null
                    : SavingsCalculator.switchSavings(reference, q.unitPrice(), annualQuantity);
            return new ComparisonView.Offer(r.rank(), r.eligible(), r.exclusionReason(), r.rank() == 1, q.id(),
                    q.marketSupplierId(), q.supplierName(), q.offeredName(), q.equivalence(), q.unitPrice(),
                    q.minQuantity(), r.orderQuantity(), r.totalCost(), r.effectiveUnitCost(), q.leadTimeDays(),
                    q.availability(), q.qualityScore(), q.validUntil(), q.source(), r.score(), r.unitSaving(),
                    r.savingPct(), annual);
        }).toList();
        return new ComparisonView(product.id(), product.reference(), product.name(), needed, reference,
                properties.currency(), Math.round(monthly * 10) / 10.0, offers);
    }

    /** Articles ayant reçu au moins un devis. */
    public java.util.Set<Long> productIdsWithQuotes() {
        return quotes.productIds();
    }

    public Optional<Quote> findById(Long id) {
        return quotes.findById(id);
    }

    BigDecimal withoutVat(BigDecimal price, boolean includesVat) {
        if (price == null || !includesVat) {
            return price;
        }
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(properties.vatRatePct())
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        return price.divide(factor, 3, RoundingMode.HALF_UP);
    }

    private double consumption(Long productId) {
        try {
            StockInfo info = stock.stockSituation(List.of(productId), properties.consumptionLookbackDays())
                    .get(productId);
            return info == null ? 0 : info.monthlyConsumption();
        } catch (RuntimeException e) {
            return 0; // la comparaison reste possible sans les données de stock
        }
    }
}
