package ma.pfe.stock.supplier.application.service;

import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.supplier.application.dto.SupplierCommand;
import ma.pfe.stock.supplier.application.dto.SupplierView;
import ma.pfe.stock.supplier.domain.model.Countries;
import ma.pfe.stock.supplier.domain.model.Supplier;
import ma.pfe.stock.supplier.domain.port.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/** Cas d'utilisation : gestion des fournisseurs. */
@Service
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SupplierView create(SupplierCommand cmd) {
        String code = cmd.code().trim().toUpperCase();
        if (repository.existsByCode(code)) {
            throw new DuplicateResourceException("Le code fournisseur '" + code + "' existe déjà");
        }
        Supplier supplier = Supplier.create(code, cmd.name(), cmd.contactName(), cmd.email(), cmd.phone(),
                cmd.address(), cmd.city(), cmd.country(), cmd.taxId(), cmd.notes());
        if (supplier.getTaxId() != null
                && repository.existsByCountryAndTaxId(supplier.getCountry(), supplier.getTaxId())) {
            throw new DuplicateResourceException("Un fournisseur avec l'identifiant fiscal '" + supplier.getTaxId()
                    + "' existe déjà pour ce pays");
        }
        return SupplierView.from(repository.save(supplier));
    }

    @Transactional
    public SupplierView update(Long id, SupplierCommand cmd) {
        Supplier supplier = get(id);
        String code = cmd.code().trim().toUpperCase();
        if (repository.existsByCodeAndIdNot(code, id)) {
            throw new DuplicateResourceException("Le code fournisseur '" + code + "' existe déjà");
        }
        supplier.update(code, cmd.name(), cmd.contactName(), cmd.email(), cmd.phone(), cmd.address(), cmd.city(),
                cmd.country(), cmd.taxId(), cmd.notes());
        if (supplier.getTaxId() != null
                && repository.existsByCountryAndTaxIdAndIdNot(supplier.getCountry(), supplier.getTaxId(), id)) {
            throw new DuplicateResourceException("Un fournisseur avec l'identifiant fiscal '" + supplier.getTaxId()
                    + "' existe déjà pour ce pays");
        }
        return SupplierView.from(repository.save(supplier));
    }

    @Transactional
    public SupplierView setActive(Long id, boolean active) {
        Supplier supplier = get(id);
        if (active) {
            supplier.activate();
        } else {
            supplier.deactivate();
        }
        return SupplierView.from(repository.save(supplier));
    }

    @Transactional(readOnly = true)
    public SupplierView findById(Long id) {
        return SupplierView.from(get(id));
    }

    @Transactional(readOnly = true)
    public List<SupplierView> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIds(ids).stream().map(SupplierView::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<SupplierView> search(String keyword, Boolean active, String country, int page, int size) {
        return repository.search(keyword, active, country, page, size).map(SupplierView::from);
    }

    /** Liste de tous les pays (ISO 3166-1), triés par nom français. */
    public List<Countries.Country> countries() {
        return Countries.all();
    }

    private Supplier get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fournisseur", id));
    }
}
