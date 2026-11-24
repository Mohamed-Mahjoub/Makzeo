package ma.pfe.stock.procurement.application.service;

import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.application.dto.MarketSupplierView;
import ma.pfe.stock.procurement.application.dto.SupplierCommand;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.port.MarketSupplierRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Référentiel des fournisseurs du marché : catalogue, veille et saisie manuelle. */
@Service
public class MarketSupplierService {

    /** Fournisseur rapproché ou créé, et s'il vient d'être découvert. */
    public record Upsert(MarketSupplier supplier, boolean created) {
    }

    private final MarketSupplierRepository repository;

    public MarketSupplierService(MarketSupplierRepository repository) {
        this.repository = repository;
    }

    public PageResult<MarketSupplierView> search(MarketSupplierStatus status, int page, int size) {
        return repository.search(status, page, size).map(MarketSupplierView::from);
    }

    public MarketSupplierView create(SupplierCommand cmd) {
        MarketSupplier supplier = MarketSupplier.manual(cmd.name(), cmd.website(), cmd.email(), cmd.phone(),
                cmd.city(), cmd.notes());
        if (repository.findByNormalizedKey(supplier.getNormalizedKey()).isPresent()) {
            throw new DuplicateResourceException("Le fournisseur '" + cmd.name() + "' existe déjà");
        }
        return MarketSupplierView.from(repository.save(supplier));
    }

    public MarketSupplierView update(Long id, SupplierCommand cmd) {
        MarketSupplier supplier = get(id);
        supplier.updateDetails(cmd.name(), cmd.website(), cmd.email(), cmd.phone(), cmd.city(), cmd.notes());
        return MarketSupplierView.from(repository.save(supplier));
    }

    public MarketSupplierView validate(Long id) {
        MarketSupplier supplier = get(id);
        supplier.validate();
        return MarketSupplierView.from(repository.save(supplier));
    }

    public MarketSupplierView reject(Long id) {
        MarketSupplier supplier = get(id);
        supplier.reject();
        return MarketSupplierView.from(repository.save(supplier));
    }

    public MarketSupplier get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fournisseur du marché", id));
    }

    /** Rapproche un fournisseur trouvé par la veille de ceux déjà connus, ou le crée comme candidat. */
    public Upsert upsertFinding(MarketIntelligence.SupplierFinding finding) {
        String key = MarketSupplier.normalizeKey(finding.name());
        Optional<MarketSupplier> existing = repository.findByNormalizedKey(key);
        if (existing.isPresent()) {
            MarketSupplier supplier = existing.get();
            supplier.completeContact(finding.website(), finding.email(), finding.phone(), finding.city());
            return new Upsert(repository.save(supplier), false);
        }
        MarketSupplier created = MarketSupplier.discovered(finding.name(), finding.website(), finding.email(),
                finding.phone(), finding.city());
        return new Upsert(repository.save(created), true);
    }

    /** Importe les fournisseurs du catalogue (validés d'office). Renvoie le nombre de nouveaux. */
    public int syncCatalog(List<CatalogGateway.CatalogSupplier> catalogSuppliers) {
        int created = 0;
        for (CatalogGateway.CatalogSupplier cs : catalogSuppliers) {
            Optional<MarketSupplier> linked = repository.findByCatalogSupplierId(cs.id());
            if (linked.isPresent()) {
                MarketSupplier s = linked.get();
                s.completeContact(null, cs.email(), cs.phone(), cs.city());
                repository.save(s);
                continue;
            }
            Optional<MarketSupplier> sameName = repository.findByNormalizedKey(MarketSupplier.normalizeKey(cs.name()));
            if (sameName.isPresent()) {
                MarketSupplier s = sameName.get();
                s.linkToCatalog(cs.id());
                s.completeContact(null, cs.email(), cs.phone(), cs.city());
                s.validate();
                repository.save(s);
            } else {
                repository.save(MarketSupplier.fromCatalog(cs.id(), cs.name(), cs.email(), cs.phone(), cs.city()));
                created++;
            }
        }
        return created;
    }

    public long count(MarketSupplierStatus status) {
        return repository.countByStatus(status);
    }
}
