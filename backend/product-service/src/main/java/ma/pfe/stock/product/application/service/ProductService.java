package ma.pfe.stock.product.application.service;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.product.application.dto.ProductCommand;
import ma.pfe.stock.product.application.dto.ProductView;
import ma.pfe.stock.product.application.port.BarcodeSequence;
import ma.pfe.stock.product.application.port.SupplierDirectory;
import ma.pfe.stock.product.domain.model.Barcode;
import ma.pfe.stock.product.domain.model.Category;
import ma.pfe.stock.product.domain.model.Product;
import ma.pfe.stock.product.domain.port.CategoryRepository;
import ma.pfe.stock.product.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Cas d'utilisation : gestion du catalogue produits. */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BarcodeSequence barcodeSequence;
    private final SupplierDirectory supplierDirectory;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          BarcodeSequence barcodeSequence, SupplierDirectory supplierDirectory) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.barcodeSequence = barcodeSequence;
        this.supplierDirectory = supplierDirectory;
    }

    @Transactional
    public ProductView create(ProductCommand cmd) {
        String reference = cmd.reference().trim().toUpperCase();
        if (productRepository.existsByReference(reference)) {
            throw new DuplicateResourceException("La référence '" + reference + "' existe déjà");
        }
        Barcode barcode = resolveBarcode(cmd.barcode());
        if (productRepository.existsByBarcode(barcode.value())) {
            throw new DuplicateResourceException("Le code-barres '" + barcode + "' est déjà attribué");
        }
        Category category = getCategory(cmd.categoryId());
        checkSupplier(cmd.supplierId());
        Product product = Product.create(reference, cmd.name(), cmd.description(), category.getId(), barcode,
                cmd.unit(), cmd.purchasePrice(), cmd.salePrice(), cmd.minStock(), cmd.maxStock(),
                cmd.supplierId());
        return ProductView.from(productRepository.save(product), category.getName());
    }

    @Transactional
    public ProductView update(Long id, ProductCommand cmd) {
        Product product = get(id);
        String reference = cmd.reference().trim().toUpperCase();
        if (productRepository.existsByReferenceAndIdNot(reference, id)) {
            throw new DuplicateResourceException("La référence '" + reference + "' existe déjà");
        }
        Barcode barcode = cmd.barcode() == null || cmd.barcode().isBlank()
                ? product.getBarcode()
                : new Barcode(cmd.barcode());
        if (productRepository.existsByBarcodeAndIdNot(barcode.value(), id)) {
            throw new DuplicateResourceException("Le code-barres '" + barcode + "' est déjà attribué");
        }
        Category category = getCategory(cmd.categoryId());
        if (!Objects.equals(product.getSupplierId(), cmd.supplierId())) {
            checkSupplier(cmd.supplierId());
        }
        product.update(reference, cmd.name(), cmd.description(), category.getId(), barcode, cmd.unit(),
                cmd.purchasePrice(), cmd.salePrice(), cmd.minStock(), cmd.maxStock(), cmd.supplierId());
        return ProductView.from(productRepository.save(product), category.getName());
    }

    @Transactional
    public ProductView setActive(Long id, boolean active) {
        Product product = get(id);
        if (active) {
            product.activate();
        } else {
            product.deactivate();
        }
        return toView(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductView findById(Long id) {
        return toView(get(id));
    }

    @Transactional(readOnly = true)
    public ProductView findByReference(String reference) {
        return productRepository.findByReference(reference.trim().toUpperCase())
                .map(this::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Produit (référence)", reference));
    }

    @Transactional(readOnly = true)
    public ProductView findByBarcode(String barcode) {
        return productRepository.findByBarcode(barcode.trim().toUpperCase())
                .map(this::toView)
                .orElseThrow(() -> new ResourceNotFoundException("Produit (code-barres)", barcode));
    }

    @Transactional(readOnly = true)
    public List<ProductView> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Product> products = productRepository.findAllByIds(ids);
        Map<Long, String> categoryNames = categoryNames();
        return products.stream().map(p -> ProductView.from(p, categoryNames.get(p.getCategoryId()))).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<ProductView> search(ProductRepository.ProductCriteria criteria, int page, int size) {
        PageResult<Product> result = productRepository.search(criteria, page, size);
        Map<Long, String> categoryNames = categoryNames();
        return result.map(p -> ProductView.from(p, categoryNames.get(p.getCategoryId())));
    }

    private Barcode resolveBarcode(String raw) {
        if (raw == null || raw.isBlank()) {
            Barcode generated;
            do {
                generated = Barcode.internalEan13(barcodeSequence.next());
            } while (productRepository.existsByBarcode(generated.value()));
            return generated;
        }
        return new Barcode(raw);
    }

    private void checkSupplier(Long supplierId) {
        if (supplierId != null && !supplierDirectory.exists(supplierId)) {
            throw new BusinessRuleException("SUPPLIER_NOT_FOUND", "Fournisseur introuvable : " + supplierId);
        }
    }

    private Product get(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Produit", id));
    }

    private Category getCategory(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("La catégorie est obligatoire");
        }
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
    }

    private ProductView toView(Product product) {
        String categoryName = categoryRepository.findById(product.getCategoryId()).map(Category::getName)
                .orElse(null);
        return ProductView.from(product, categoryName);
    }

    private Map<Long, String> categoryNames() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

}
