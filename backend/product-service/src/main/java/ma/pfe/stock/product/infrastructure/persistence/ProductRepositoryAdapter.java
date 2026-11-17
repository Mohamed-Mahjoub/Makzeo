package ma.pfe.stock.product.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.product.application.port.BarcodeSequence;
import ma.pfe.stock.product.domain.model.Barcode;
import ma.pfe.stock.product.domain.model.Product;
import ma.pfe.stock.product.domain.port.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Adaptateur de persistance des produits (et séquence des codes-barres internes). */
@Component
public class ProductRepositoryAdapter implements ProductRepository, BarcodeSequence {

    private final ProductJpaRepository jpa;

    public ProductRepositoryAdapter(ProductJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Product save(Product p) {
        ProductEntity e = p.getId() == null ? new ProductEntity() : jpa.findById(p.getId()).orElseGet(ProductEntity::new);
        e.setReference(p.getReference());
        e.setName(p.getName());
        e.setDescription(p.getDescription());
        e.setCategoryId(p.getCategoryId());
        e.setBarcode(p.getBarcode().value());
        e.setUnit(p.getUnit());
        e.setPurchasePrice(p.getPurchasePrice());
        e.setSalePrice(p.getSalePrice());
        e.setMinStock(p.getMinStock());
        e.setMaxStock(p.getMaxStock());
        e.setSupplierId(p.getSupplierId());
        e.setActive(p.isActive());
        e.setCreatedAt(p.getCreatedAt());
        e.setUpdatedAt(p.getUpdatedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Product> findByReference(String reference) {
        return jpa.findByReference(reference).map(this::toDomain);
    }

    @Override
    public Optional<Product> findByBarcode(String barcode) {
        return jpa.findByBarcode(barcode).map(this::toDomain);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return jpa.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByReference(String reference) {
        return jpa.existsByReference(reference);
    }

    @Override
    public boolean existsByReferenceAndIdNot(String reference, Long id) {
        return jpa.existsByReferenceAndIdNot(reference, id);
    }

    @Override
    public boolean existsByBarcode(String barcode) {
        return jpa.existsByBarcode(barcode);
    }

    @Override
    public boolean existsByBarcodeAndIdNot(String barcode, Long id) {
        return jpa.existsByBarcodeAndIdNot(barcode, id);
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return jpa.countByCategoryId(categoryId);
    }

    @Override
    public PageResult<Product> search(ProductCriteria criteria, int page, int size) {
        Specification<ProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String like = "%" + criteria.keyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("reference")), like),
                        cb.like(cb.lower(root.get("barcode")), like)));
            }
            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), criteria.categoryId()));
            }
            if (criteria.supplierId() != null) {
                predicates.add(cb.equal(root.get("supplierId"), criteria.supplierId()));
            }
            if (criteria.active() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.active()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ProductEntity> result = jpa.findAll(spec, PageRequest.of(page, size, Sort.by("name")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(), page, size,
                result.getTotalElements());
    }

    @Override
    public long next() {
        return jpa.nextBarcodeSequence();
    }

    private Product toDomain(ProductEntity e) {
        return new Product(e.getId(), e.getReference(), e.getName(), e.getDescription(), e.getCategoryId(),
                new Barcode(e.getBarcode()), e.getUnit(), e.getPurchasePrice(), e.getSalePrice(), e.getMinStock(),
                e.getMaxStock(), e.getSupplierId(), e.isActive(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
