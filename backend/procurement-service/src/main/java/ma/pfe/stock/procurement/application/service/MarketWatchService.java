package ma.pfe.stock.procurement.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.application.dto.WatchRunView;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.application.port.StockGateway;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.domain.model.SourcingLead;
import ma.pfe.stock.procurement.domain.model.StockInfo;
import ma.pfe.stock.procurement.domain.model.WatchRun;
import ma.pfe.stock.procurement.domain.port.MarketSupplierRepository;
import ma.pfe.stock.procurement.domain.port.SourcingLeadRepository;
import ma.pfe.stock.procurement.domain.port.WatchRunRepository;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import ma.pfe.stock.procurement.infrastructure.security.SystemAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Veille du marché, mensuelle ou à la demande. Pour chaque article actif :
 * <ol>
 *   <li>relevé du prix de la fiche produit (historique) ;</li>
 *   <li>recherche par l'agent IA des fournisseurs proposant l'article ou un équivalent, et des prix publiés ;</li>
 *   <li>tendance de prix du marché, puis prédiction ;</li>
 *   <li>préparation de demandes de prix pour les fournisseurs validés (envoi après validation humaine) ;</li>
 *   <li>recommandations d'achat, puis alerte du responsable des achats.</li>
 * </ol>
 */
@Service
public class MarketWatchService {

    private static final Logger log = LoggerFactory.getLogger(MarketWatchService.class);
    private static final Duration STALE_AFTER = Duration.ofHours(6);

    private final WatchRunRepository runs;
    private final CatalogGateway catalog;
    private final StockGateway stock;
    private final MarketIntelligence intelligence;
    private final MarketSupplierService suppliers;
    private final MarketSupplierRepository supplierRepository;
    private final SourcingLeadRepository leads;
    private final QuoteService quotes;
    private final RfqService rfqs;
    private final PriceService prices;
    private final RecommendationService recommendations;
    private final SystemAuthentication systemAuthentication;
    private final ExecutorService watchExecutor;
    private final ProcurementProperties properties;
    private final ZoneId zone;
    private final MeterRegistry meters;

    public MarketWatchService(WatchRunRepository runs, CatalogGateway catalog, StockGateway stock,
                              MarketIntelligence intelligence, MarketSupplierService suppliers,
                              MarketSupplierRepository supplierRepository, SourcingLeadRepository leads,
                              QuoteService quotes, RfqService rfqs, PriceService prices,
                              RecommendationService recommendations, SystemAuthentication systemAuthentication,
                              ExecutorService watchExecutor, ProcurementProperties properties, ZoneId zone,
                              MeterRegistry meters) {
        this.runs = runs;
        this.catalog = catalog;
        this.stock = stock;
        this.intelligence = intelligence;
        this.suppliers = suppliers;
        this.supplierRepository = supplierRepository;
        this.leads = leads;
        this.quotes = quotes;
        this.rfqs = rfqs;
        this.prices = prices;
        this.recommendations = recommendations;
        this.systemAuthentication = systemAuthentication;
        this.watchExecutor = watchExecutor;
        this.properties = properties;
        this.zone = zone;
        this.meters = meters;
    }

    /** Démarre une veille en arrière-plan. Une seule veille peut tourner à la fois. */
    public WatchRunView start(WatchRun.Trigger trigger, String user) {
        releaseStaleRun();
        if (runs.findRunning().isPresent()) {
            throw new BusinessRuleException("WATCH_RUNNING", "Une veille est déjà en cours");
        }
        WatchRun run;
        try {
            run = runs.save(WatchRun.start(YearMonth.now(zone).toString(), trigger, user));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("WATCH_RUNNING", "Une veille est déjà en cours");
        }
        Long runId = run.getId();
        watchExecutor.submit(() -> systemAuthentication.runAsAgent(() -> execute(runId)));
        return WatchRunView.from(run);
    }

    /** Veille planifiée : une seule exécution réussie par mois. */
    public void startScheduled() {
        if (!properties.watchEnabled()) {
            return;
        }
        String period = YearMonth.now(zone).toString();
        if (runs.existsCompleted(period)) {
            log.info("Veille de {} déjà effectuée", period);
            return;
        }
        try {
            start(WatchRun.Trigger.SCHEDULED, "planificateur");
        } catch (BusinessRuleException e) {
            log.info("Veille planifiée non lancée : {}", e.getMessage());
        }
    }

    public PageResult<WatchRunView> history(int page, int size) {
        return runs.history(page, size).map(WatchRunView::from);
    }

    public Optional<WatchRunView> latest() {
        return runs.findLatest().map(WatchRunView::from);
    }

    public List<SourcingLead> leads(Long productId) {
        return productId == null ? leads.findRecent(200) : leads.findByProduct(productId);
    }

    void execute(Long runId) {
        WatchRun run = runs.findById(runId).orElseThrow();
        int productsScanned = 0;
        int discovered = 0;
        int leadsFound = 0;
        int rfqsDrafted = 0;
        int newRecommendations = 0;
        List<String> warnings = new ArrayList<>();
        try {
            discovered += suppliers.syncCatalog(catalog.activeSuppliers());
            List<ProductInfo> products = catalog.activeProducts(properties.maxProductsPerRun());
            Map<Long, StockInfo> situation = stock.stockSituation(products.stream().map(ProductInfo::id).toList(),
                    properties.consumptionLookbackDays());
            if (!intelligence.enabled()) {
                warnings.add("Veille web non configurée (SERPER_API_KEY ou AI_API_KEY) : prédictions statistiques seules.");
            }

            for (ProductInfo product : products) {
                StockInfo info = situation.getOrDefault(product.id(), StockInfo.unknown(product.id()));
                try {
                    prices.snapshotCatalogPrice(product);
                    Map<Long, MarketSupplier> productSuppliers = new LinkedHashMap<>();

                    // fournisseur habituel de l'article (catalogue)
                    if (product.supplierId() != null) {
                        supplierRepository.findByCatalogSupplierId(product.supplierId())
                                .ifPresent(s -> productSuppliers.put(s.getId(), s));
                    }

                    // veille web
                    Double signal = null;
                    if (intelligence.enabled()) {
                        for (MarketIntelligence.SupplierFinding f : intelligence.findSuppliers(product,
                                properties.maxSuppliersPerProduct())) {
                            MarketSupplierService.Upsert upsert = suppliers.upsertFinding(f);
                            if (upsert.created()) {
                                discovered++;
                            }
                            MarketSupplier s = upsert.supplier();
                            if (s.getStatus() == MarketSupplierStatus.REJECTED) {
                                continue;
                            }
                            productSuppliers.putIfAbsent(s.getId(), s);
                            leads.save(new SourcingLead(null, product.id(), s.getId(), s.getName(), f.offeredName(),
                                    f.offeredReference(), f.url(), f.price(), f.currency(), f.equivalence(),
                                    f.similarity(), f.rationale(), runId, Instant.now()));
                            leadsFound++;
                            if (f.price() != null && f.equivalence() == Equivalence.IDENTICAL) {
                                var quote = quotes.registerWebOffer(product, s, f);
                                prices.record(product.id(), quote.unitPrice(), LocalDate.now(zone), PriceSource.MARKET,
                                        s.getName(), null);
                            }
                        }
                        signal = intelligence.marketSignal(product, properties.forecastHorizonMonths())
                                .map(MarketIntelligence.MarketSignal::expectedChangePct).orElse(null);
                    }

                    // demandes de prix (brouillons) vers les fournisseurs validés joignables
                    int quantity = (int) Math.max(1, Math.max(Math.ceil(info.monthlyConsumption() * 3),
                            product.minStock() * 2L));
                    int drafted = 0;
                    for (MarketSupplier s : productSuppliers.values()) {
                        if (drafted >= properties.maxRfqsPerProduct()) {
                            break;
                        }
                        if (s.canReceiveRfq() && rfqs.draftFor(product, s, quantity,
                                SystemAuthentication.AGENT_USER).isPresent()) {
                            drafted++;
                        }
                    }
                    rfqsDrafted += drafted;

                    prices.predict(product, signal);
                    newRecommendations += recommendations.refresh(product, info);
                    productsScanned++;
                } catch (RuntimeException e) {
                    log.warn("Veille : article {} ignoré ({})", product.reference(), e.getMessage());
                    warnings.add(product.reference() + " : " + e.getMessage());
                }
            }
            recommendations.notifyOpportunities();
            run.complete(new WatchRun.Stats(productsScanned, discovered, leadsFound, rfqsDrafted, newRecommendations),
                    warnings.isEmpty() ? null : String.join(" | ", warnings));
            meters.counter("makzeo_procurement_watch_runs_total", "status", "completed").increment();
        } catch (RuntimeException e) {
            log.error("Veille {} en échec", runId, e);
            run.fail(new WatchRun.Stats(productsScanned, discovered, leadsFound, rfqsDrafted, newRecommendations),
                    e.getMessage());
            meters.counter("makzeo_procurement_watch_runs_total", "status", "failed").increment();
        }
        runs.save(run);
        log.info("Veille {} terminée : {} articles, {} fournisseurs découverts, {} opportunités, {} RFQ, {}"
                + " recommandations", runId, productsScanned, discovered, leadsFound, rfqsDrafted, newRecommendations);
    }

    /** Libère une veille restée "en cours" après un arrêt brutal du service. */
    private void releaseStaleRun() {
        runs.findRunning().ifPresent(r -> {
            if (r.getStartedAt().isBefore(Instant.now().minus(STALE_AFTER))) {
                r.fail(r.getStats(), "Veille interrompue (arrêt du service)");
                runs.save(r);
            }
        });
    }
}
