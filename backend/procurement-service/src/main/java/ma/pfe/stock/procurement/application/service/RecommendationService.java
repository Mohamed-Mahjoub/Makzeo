package ma.pfe.stock.procurement.application.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.application.dto.RecommendationView;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.application.port.EmailSender;
import ma.pfe.stock.procurement.application.port.StockGateway;
import ma.pfe.stock.procurement.domain.model.ConfidenceLevel;
import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.domain.model.PurchaseRecommendation;
import ma.pfe.stock.procurement.domain.model.Quote;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
import ma.pfe.stock.procurement.domain.model.StockInfo;
import ma.pfe.stock.procurement.domain.port.PurchaseRecommendationRepository;
import ma.pfe.stock.procurement.domain.service.AnticipatedPurchaseOptimizer;
import ma.pfe.stock.procurement.domain.service.OfferComparator;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recommandations d'achat : achat anticipé avant une hausse prévue, changement de fournisseur quand une
 * offre est nettement moins chère que le dernier prix payé. Alerte du responsable des achats.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final PurchaseRecommendationRepository repository;
    private final PriceService prices;
    private final QuoteService quotes;
    private final CatalogGateway catalog;
    private final StockGateway stock;
    private final EmailSender mail;
    private final ProcurementProperties properties;
    private final ZoneId zone;
    private final MeterRegistry meters;

    public RecommendationService(PurchaseRecommendationRepository repository, PriceService prices,
                                 QuoteService quotes, CatalogGateway catalog, StockGateway stock, EmailSender mail,
                                 ProcurementProperties properties, ZoneId zone, MeterRegistry meters) {
        this.repository = repository;
        this.prices = prices;
        this.quotes = quotes;
        this.catalog = catalog;
        this.stock = stock;
        this.mail = mail;
        this.properties = properties;
        this.zone = zone;
        this.meters = meters;
        Gauge.builder("makzeo_procurement_open_opportunities", this, s -> s.openCount())
                .description("Recommandations d'achat en attente de décision").register(meters);
        Gauge.builder("makzeo_procurement_potential_savings", this, s -> s.openSavings())
                .description("Économies potentielles des recommandations ouvertes (devise du module)")
                .register(meters);
    }

    public PageResult<RecommendationView> search(RecommendationStatus status, RecommendationType type, int page,
                                                 int size) {
        return repository.search(status, type, page, size).map(RecommendationView::from);
    }

    public RecommendationView accept(Long id, String user) {
        PurchaseRecommendation r = get(id);
        r.accept(user);
        return RecommendationView.from(repository.save(r));
    }

    public RecommendationView dismiss(Long id, String user) {
        PurchaseRecommendation r = get(id);
        r.dismiss(user);
        return RecommendationView.from(repository.save(r));
    }

    /** Recalcule les recommandations de tous les articles actifs (sans appel à l'IA). */
    public int refreshAll() {
        List<ProductInfo> products = catalog.activeProducts(2000);
        Map<Long, StockInfo> situation = stock.stockSituation(products.stream().map(ProductInfo::id).toList(),
                properties.consumptionLookbackDays());
        int created = 0;
        for (ProductInfo p : products) {
            created += refresh(p, situation.getOrDefault(p.id(), StockInfo.unknown(p.id())));
        }
        return created;
    }

    /** Recalcule les recommandations d'un article. Renvoie le nombre de nouvelles recommandations. */
    public int refresh(ProductInfo product, StockInfo situation) {
        int created = 0;
        BigDecimal reference = prices.referencePrice(product);
        Optional<OfferComparator.RankedOffer> best = bestOffer(product, situation, reference);

        // 1. changement de fournisseur
        if (reference != null && best.isPresent()) {
            Quote q = best.get().quote();
            BigDecimal threshold = reference.multiply(BigDecimal.valueOf(1 - properties.minSwitchSavingPct() / 100));
            if (q.unitPrice().compareTo(threshold) < 0) {
                int annualQty = (int) Math.max(1, Math.round(situation.monthlyConsumption() * 12));
                BigDecimal saving = SavingsCalculator.switchSavings(reference, q.unitPrice(), annualQty);
                BigDecimal costNow = q.unitPrice().multiply(BigDecimal.valueOf(annualQty)).setScale(3, RoundingMode.HALF_UP);
                BigDecimal costLater = reference.multiply(BigDecimal.valueOf(annualQty)).setScale(3, RoundingMode.HALF_UP);
                String rationale = String.format(Locale.FRANCE,
                        "%s propose %s à %s %s HT contre %s %s payés en dernier (%s%%), soit %s %s d'économie"
                                + " sur un an de consommation (%d unités).",
                        q.supplierName(), q.offeredName(), q.unitPrice().toPlainString(), properties.currency(),
                        reference.toPlainString(), properties.currency(),
                        best.get().savingPct() == null ? "-" : "-" + best.get().savingPct().toPlainString(),
                        saving.toPlainString(), properties.currency(), annualQty);
                created += replaceOpen(product, RecommendationType.SWITCH_SUPPLIER, q.id(), annualQty,
                        () -> new PurchaseRecommendation(null, RecommendationType.SWITCH_SUPPLIER,
                                RecommendationStatus.OPEN, product.id(), product.reference(), product.name(),
                                q.supplierName(), q.id(), annualQty, reference, q.unitPrice(), null, costNow,
                                costLater, BigDecimal.ZERO.setScale(3), saving, null, null, rationale, null, null,
                                null, null));
            }
        }

        // 2. achat anticipé avant une hausse prévue
        Optional<PricePrediction> prediction = prices.prediction(product.id());
        if (prediction.isPresent() && prediction.get().changePct().doubleValue() >= properties.minChangePct()
                && prediction.get().confidenceLevel() != ConfidenceLevel.LOW) {
            PricePrediction pp = prediction.get();
            BigDecimal priceNow = best.map(b -> b.quote().unitPrice()).orElse(pp.currentPrice());
            String supplier = best.map(b -> b.quote().supplierName()).orElse(null);
            Long quoteId = best.map(b -> b.quote().id()).orElse(null);
            int minOrder = best.map(b -> b.quote().minQuantity()).orElse(1);
            BigDecimal future = priceNow.multiply(BigDecimal.ONE.add(pp.changePct()
                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))).setScale(3, RoundingMode.HALF_UP);
            int monthsUntil = Math.max(1, pp.horizonDays() / 30);
            Optional<AnticipatedPurchaseOptimizer.Plan> plan = AnticipatedPurchaseOptimizer.optimize(
                    new AnticipatedPurchaseOptimizer.Input(situation.quantity(), situation.monthlyConsumption(),
                            priceNow, future, monthsUntil, properties.monthlyHoldingRate(),
                            properties.maxCoverMonths(), properties.purchaseBudget(), minOrder, null));
            if (plan.isPresent()) {
                AnticipatedPurchaseOptimizer.Plan pl = plan.get();
                LocalDate buyBefore = LocalDate.now(zone).plusDays(Math.max(7, pp.horizonDays() / 3));
                String rationale = String.format(Locale.FRANCE,
                        "Hausse prévue de %s %% sous %d jours (confiance %s). Acheter %d unités maintenant à %s %s"
                                + " couvre %d mois de besoins : coût %s %s + stockage %s %s, contre %s %s en achetant"
                                + " au fil de l'eau. Gain net estimé : %s %s.",
                        pp.changePct().toPlainString(), pp.horizonDays(), pp.confidenceLevel().label().toLowerCase(),
                        pl.quantity(), priceNow.toPlainString(), properties.currency(), pl.coverMonths(),
                        pl.costNow().toPlainString(), properties.currency(), pl.holdingCost().toPlainString(),
                        properties.currency(), pl.costLater().toPlainString(), properties.currency(),
                        pl.netSavings().toPlainString(), properties.currency());
                BigDecimal ref = reference != null ? reference : priceNow;
                created += replaceOpen(product, RecommendationType.ANTICIPATED_PURCHASE, quoteId, pl.quantity(),
                        () -> new PurchaseRecommendation(null, RecommendationType.ANTICIPATED_PURCHASE,
                                RecommendationStatus.OPEN, product.id(), product.reference(), product.name(),
                                supplier, quoteId, pl.quantity(), ref, priceNow, future, pl.costNow(),
                                pl.costLater(), pl.holdingCost(), pl.netSavings(), buyBefore, pp.confidence(),
                                rationale, null, null, null, null));
            }
        }
        return created;
    }

    /** Envoie au responsable des achats la liste des nouvelles opportunités (une fois par opportunité). */
    public int notifyOpportunities() {
        List<PurchaseRecommendation> pending = repository.findOpenNotNotified();
        List<String> recipients = properties.purchasingManagerEmails().stream()
                .filter(e -> e != null && !e.isBlank()).map(String::trim).toList();
        if (pending.isEmpty() || recipients.isEmpty() || !mail.configured()) {
            return 0;
        }
        StringBuilder body = new StringBuilder("Bonjour,\n\nL'agent achats a détecté ")
                .append(pending.size()).append(" opportunité(s) :\n\n");
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseRecommendation r : pending) {
            body.append(r.getType() == RecommendationType.ANTICIPATED_PURCHASE ? "- Achat anticipé : "
                            : "- Changement de fournisseur : ")
                    .append(r.getProductReference()).append(" ").append(r.getProductName())
                    .append(" | ").append(r.getQuantity()).append(" unités")
                    .append(r.getSupplierName() != null ? " chez " + r.getSupplierName() : "")
                    .append(" | gain estimé ").append(r.getNetSavings().toPlainString()).append(' ')
                    .append(properties.currency())
                    .append(r.getBuyBefore() != null ? " | à décider avant le " + r.getBuyBefore() : "")
                    .append('\n');
            total = total.add(r.getNetSavings());
        }
        body.append("\nÉconomie potentielle totale : ").append(total.toPlainString()).append(' ')
                .append(properties.currency())
                .append("\n\nDétail et décision : ").append(properties.appUrl()).append("/achats/recommandations")
                .append("\n\nCe message est envoyé automatiquement par Makzeo.");
        try {
            mail.send(recipients,
                    "Makzeo - " + pending.size() + " opportunité(s) d'achat à examiner", body.toString(), null);
            pending.forEach(r -> {
                r.markNotified();
                repository.save(r);
            });
            meters.counter("makzeo_procurement_alerts_total").increment();
            return pending.size();
        } catch (EmailSender.EmailException e) {
            log.warn("Alerte achats non envoyée : {}", e.getMessage());
            return 0;
        }
    }

    public List<PurchaseRecommendation> all() {
        return repository.findCreatedSince(java.time.Instant.now().minus(java.time.Duration.ofDays(400)));
    }

    private Optional<OfferComparator.RankedOffer> bestOffer(ProductInfo product, StockInfo situation,
                                                            BigDecimal reference) {
        List<Quote> productQuotes = quotes.findByProduct(product.id());
        if (productQuotes.isEmpty()) {
            return Optional.empty();
        }
        int needed = (int) Math.max(1, Math.max(Math.ceil(situation.monthlyConsumption() * 3), product.minStock()));
        return OfferComparator.compare(productQuotes, needed, reference, LocalDate.now(zone), zone,
                        OfferComparator.Weights.DEFAULT).stream()
                .filter(r -> r.rank() == 1).findFirst();
    }

    /** Remplace la recommandation ouverte du même type si elle diffère ; sinon la conserve. */
    private int replaceOpen(ProductInfo product, RecommendationType type, Long quoteId, int quantity,
                            java.util.function.Supplier<PurchaseRecommendation> factory) {
        List<PurchaseRecommendation> open = repository.findOpen(product.id(), type);
        boolean unchanged = open.stream().anyMatch(r -> java.util.Objects.equals(r.getQuoteId(), quoteId)
                && r.getQuantity() == quantity);
        if (unchanged) {
            return 0;
        }
        open.forEach(r -> {
            r.expire();
            repository.save(r);
        });
        repository.save(factory.get());
        meters.counter("makzeo_procurement_opportunities_total", "type", type.name()).increment();
        return 1;
    }

    private PurchaseRecommendation get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recommandation", id));
    }

    private double openCount() {
        try {
            return repository.search(RecommendationStatus.OPEN, null, 0, 1).totalElements();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private double openSavings() {
        try {
            return repository.search(RecommendationStatus.OPEN, null, 0, 1000).content().stream()
                    .map(PurchaseRecommendation::getNetSavings).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .doubleValue();
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
