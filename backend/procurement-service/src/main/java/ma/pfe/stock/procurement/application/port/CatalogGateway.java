package ma.pfe.stock.procurement.application.port;

import ma.pfe.stock.procurement.domain.model.ProductInfo;

import java.util.List;
import java.util.Optional;

/** Accès au catalogue (product-service) et au référentiel fournisseurs (supplier-service). */
public interface CatalogGateway {

    List<ProductInfo> activeProducts(int max);

    Optional<ProductInfo> product(Long productId);

    List<CatalogSupplier> activeSuppliers();

    record CatalogSupplier(Long id, String code, String name, String email, String phone, String city,
                           String country) {
    }
}
