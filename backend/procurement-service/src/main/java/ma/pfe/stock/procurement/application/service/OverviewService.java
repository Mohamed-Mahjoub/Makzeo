package ma.pfe.stock.procurement.application.service;

import ma.pfe.stock.procurement.application.dto.OverviewView;
import ma.pfe.stock.procurement.application.dto.WatchRunView;
import ma.pfe.stock.procurement.application.port.EmailSender;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import ma.pfe.stock.procurement.domain.port.WatchRunRepository;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;

/** Synthèse et économies du module achats. */
@Service
public class OverviewService {

    private final MarketSupplierService suppliers;
    private final RfqService rfqs;
    private final RecommendationService recommendations;
    private final PriceService prices;
    private final WatchRunRepository runs;
    private final MarketIntelligence intelligence;
    private final EmailSender mail;
    private final ProcurementProperties properties;
    private final ZoneId zone;

    public OverviewService(MarketSupplierService suppliers, RfqService rfqs, RecommendationService recommendations,
                           PriceService prices, WatchRunRepository runs, MarketIntelligence intelligence,
                           EmailSender mail, ProcurementProperties properties, ZoneId zone) {
        this.suppliers = suppliers;
        this.rfqs = rfqs;
        this.recommendations = recommendations;
        this.prices = prices;
        this.runs = runs;
        this.intelligence = intelligence;
        this.mail = mail;
        this.properties = properties;
        this.zone = zone;
    }

    public OverviewView overview() {
        SavingsCalculator.Summary savings = savings();
        List<PricePrediction> predictions = prices.predictions();
        long rising = predictions.stream().filter(p -> p.changePct().doubleValue() >= properties.minChangePct()).count();
        long falling = predictions.stream().filter(p -> p.changePct().doubleValue() <= -properties.minChangePct())
                .count();
        long open = recommendations.search(RecommendationStatus.OPEN, null, 0, 1).totalElements();
        return new OverviewView(intelligence.enabled(), intelligence.provider(), mail.configured(), properties.currency(),
                properties.marketName(), runs.findLatest().map(WatchRunView::from).orElse(null),
                suppliers.count(MarketSupplierStatus.CANDIDATE), suppliers.count(MarketSupplierStatus.VALIDATED),
                rfqs.count(RfqStatus.DRAFT), rfqs.count(RfqStatus.SENT), open, savings.totalPotential(),
                savings.totalRealized(), rising, falling);
    }

    public SavingsCalculator.Summary savings() {
        return SavingsCalculator.summarize(recommendations.all(), zone);
    }
}
