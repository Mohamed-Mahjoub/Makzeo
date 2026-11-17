package ma.pfe.stock.product.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.product.domain.model.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    Optional<Product> findByReference(String reference);

    Optional<Product> findByBarcode(String barcode);

    List<Product> findAllByIds(Collection<Long> ids);

    boolean existsByReference(String reference);

    boolean existsByReferenceAndIdNot(String reference, Long id);

    boolean existsByBarcode(String barcode);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    long countByCategoryId(Long categoryId);

    PageResult<Product> search(ProductCriteria criteria, int page, int size);

    /** Critères de recherche multicritère. */
    record ProductCriteria(String keyword, Long categoryId, Long supplierId, Boolean active) {
    }
}
