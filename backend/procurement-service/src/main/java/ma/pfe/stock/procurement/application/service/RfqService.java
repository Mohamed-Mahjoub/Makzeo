package ma.pfe.stock.procurement.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.application.dto.RfqView;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.application.port.EmailSender;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import ma.pfe.stock.procurement.domain.port.RfqRepository;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Demandes de prix : préparées par l'agent ou un acheteur, validées par le responsable des achats,
 * puis envoyées par courriel. Rien ne part sans validation humaine.
 */
@Service
public class RfqService {

    static final List<RfqStatus> OPEN_STATUSES = List.of(RfqStatus.DRAFT, RfqStatus.APPROVED, RfqStatus.SENT,
            RfqStatus.FAILED);

    private final RfqRepository repository;
    private final MarketSupplierService suppliers;
    private final CatalogGateway catalog;
    private final MarketIntelligence intelligence;
    private final EmailSender mail;
    private final ProcurementProperties properties;
    private final ZoneId zone;
    private final MeterRegistry meters;

    public RfqService(RfqRepository repository, MarketSupplierService suppliers, CatalogGateway catalog,
                      MarketIntelligence intelligence, EmailSender mail, ProcurementProperties properties,
                      ZoneId zone, MeterRegistry meters) {
        this.repository = repository;
        this.suppliers = suppliers;
        this.catalog = catalog;
        this.intelligence = intelligence;
        this.mail = mail;
        this.properties = properties;
        this.zone = zone;
        this.meters = meters;
    }

    public PageResult<RfqView> search(RfqStatus status, Long productId, int page, int size) {
        return repository.search(status, productId, page, size).map(RfqView::from);
    }

    public RfqView findById(Long id) {
        return RfqView.from(get(id));
    }

    /** Préparation manuelle d'une demande de prix (brouillon). */
    public RfqView draft(Long productId, Long marketSupplierId, int quantity, String user) {
        ProductInfo product = catalog.product(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit", productId));
        MarketSupplier supplier = suppliers.get(marketSupplierId);
        if (supplier.getStatus() == MarketSupplierStatus.REJECTED) {
            throw new BusinessRuleException("SUPPLIER_REJECTED", "Ce fournisseur a été écarté");
        }
        return draftFor(product, supplier, quantity, user)
                .map(RfqView::from)
                .orElseThrow(() -> new BusinessRuleException("RFQ_ALREADY_OPEN",
                        "Une demande de prix est déjà en cours pour cet article auprès de " + supplier.getName()));
    }

    /** Prépare un brouillon, sauf si une demande est déjà ouverte pour ce couple article / fournisseur. */
    public Optional<Rfq> draftFor(ProductInfo product, MarketSupplier supplier, int quantity, String user) {
        if (repository.existsOpen(product.id(), supplier.getId(), OPEN_STATUSES)) {
            return Optional.empty();
        }
        LocalDate due = LocalDate.now(zone).plusDays(properties.rfqResponseDays());
        MarketIntelligence.RfqText text = intelligence.writeRfq(new MarketIntelligence.RfqRequest(product,
                supplier.getName(), quantity, due, properties.buyerCompany(), properties.buyerEmail()));
        Rfq rfq = Rfq.draft(product.id(), product.reference(), product.name(), supplier, quantity, text.subject(),
                text.body(), due, user);
        return Optional.of(repository.save(rfq));
    }

    public RfqView update(Long id, int quantity, String subject, String body, LocalDate responseDueDate) {
        Rfq rfq = get(id);
        rfq.editDraft(quantity, subject, body, responseDueDate);
        return RfqView.from(repository.save(rfq));
    }

    /** Validation par le responsable des achats, puis envoi immédiat par courriel. */
    public RfqView approveAndSend(Long id, String user) {
        Rfq rfq = get(id);
        MarketSupplier supplier = suppliers.get(rfq.getMarketSupplierId());
        if (supplier.getStatus() != MarketSupplierStatus.VALIDATED) {
            throw new BusinessRuleException("SUPPLIER_NOT_VALIDATED",
                    "Validez d'abord le fournisseur " + supplier.getName() + " avant de lui écrire");
        }
        rfq.approve(user, supplier.getEmail());
        rfq = repository.save(rfq);
        try {
            mail.send(List.of(rfq.getSupplierEmail()), rfq.getSubject(), rfq.getBody(), properties.buyerEmail());
            rfq.markSent();
            meters.counter("makzeo_procurement_rfq_total", "outcome", "sent").increment();
        } catch (EmailSender.EmailException e) {
            rfq.markFailed(e.getMessage());
            meters.counter("makzeo_procurement_rfq_total", "outcome", "failed").increment();
        }
        return RfqView.from(repository.save(rfq));
    }

    public List<RfqView> approveAndSendAll(List<Long> ids, String user) {
        List<RfqView> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(approveAndSend(id, user));
        }
        return result;
    }

    public RfqView cancel(Long id) {
        Rfq rfq = get(id);
        rfq.cancel();
        return RfqView.from(repository.save(rfq));
    }

    /** Un devis a été reçu en réponse. */
    public void markAnswered(Long id) {
        repository.findById(id).ifPresent(rfq -> {
            rfq.markAnswered();
            repository.save(rfq);
        });
    }

    public long count(RfqStatus status) {
        return repository.countByStatus(status);
    }

    private Rfq get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Demande de prix", id));
    }
}
