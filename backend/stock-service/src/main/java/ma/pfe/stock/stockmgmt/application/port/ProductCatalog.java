package ma.pfe.stock.stockmgmt.application.port;

import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;

import java.util.Collection;
import java.util.List;

/** Port : accès au catalogue produits (product-service). */
public interface ProductCatalog {

    /** @throws ma.pfe.stock.common.exception.ResourceNotFoundException si le produit n'existe pas */
    ProductSnapshot getProduct(Long productId);

    List<ProductSnapshot> getProducts(Collection<Long> productIds);
}
