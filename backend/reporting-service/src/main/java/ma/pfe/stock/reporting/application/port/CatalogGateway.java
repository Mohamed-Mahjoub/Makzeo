package ma.pfe.stock.reporting.application.port;

import ma.pfe.stock.reporting.domain.model.ProductData;

import java.util.List;

/** Port : lecture du catalogue (product-service). */
public interface CatalogGateway {

    List<ProductData> findAllProducts(Long categoryId, Boolean active);
}
