package ma.pfe.stock.supplier.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.supplier.domain.model.Supplier;
import ma.pfe.stock.supplier.domain.port.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
public class SupplierRepositoryAdapter implements SupplierRepository {

    private final SupplierJpaRepository jpa;

    public SupplierRepositoryAdapter(SupplierJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Supplier save(Supplier s) {
        SupplierEntity e = s.getId() == null ? new SupplierEntity()
                : jpa.findById(s.getId()).orElseGet(SupplierEntity::new);
        e.setCode(s.getCode());
        e.setName(s.getName());
        e.setContactName(s.getContactName());
        e.setEmail(s.getEmail());
        e.setPhone(s.getPhone());
        e.setAddress(s.getAddress());
        e.setCity(s.getCity());
        e.setCountry(s.getCountry());
        e.setTaxId(s.getTaxId());
        e.setNotes(s.getNotes());
        e.setActive(s.isActive());
        e.setCreatedAt(s.getCreatedAt());
        e.setUpdatedAt(s.getUpdatedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Supplier> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Supplier> findAllByIds(Collection<Long> ids) {
        return jpa.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsByCode(code);
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, Long id) {
        return jpa.existsByCodeAndIdNot(code, id);
    }

    @Override
    public boolean existsByCountryAndTaxId(String country, String taxId) {
        return jpa.existsByCountryAndTaxId(country, taxId);
    }

    @Override
    public boolean existsByCountryAndTaxIdAndIdNot(String country, String taxId, Long id) {
        return jpa.existsByCountryAndTaxIdAndIdNot(country, taxId, id);
    }

    @Override
    public PageResult<Supplier> search(String keyword, Boolean active, String country, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<SupplierEntity> result = jpa.search(kw, active, country == null || country.isBlank() ? null : country.trim().toUpperCase(),
                PageRequest.of(page, size, Sort.by("name")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    private Supplier toDomain(SupplierEntity e) {
        return new Supplier(e.getId(), e.getCode(), e.getName(), e.getContactName(), e.getEmail(), e.getPhone(),
                e.getAddress(), e.getCity(), e.getCountry(), e.getTaxId(), e.getNotes(), e.isActive(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
