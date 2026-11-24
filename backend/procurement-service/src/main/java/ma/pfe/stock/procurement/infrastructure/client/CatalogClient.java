package ma.pfe.stock.procurement.infrastructure.client;

import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adaptateur HTTP vers product-service et supplier-service. */
@Component
public class CatalogClient implements CatalogGateway {

    private static final int PAGE_SIZE = 200;

    private final RestClient products;
    private final RestClient suppliers;

    public CatalogClient(ProcurementProperties properties) {
        this.products = RestClientFactory.create(properties.productServiceUrl());
        this.suppliers = RestClientFactory.create(properties.supplierServiceUrl());
    }

    @Override
    public List<ProductInfo> activeProducts(int max) {
        List<ProductInfo> result = new ArrayList<>();
        int page = 0;
        RemoteResponses.Page<RemoteResponses.Product> current;
        do {
            int p = page;
            current = call("product-service", () -> products.get()
                    .uri(uri -> uri.path("/api/products").queryParam("active", true)
                            .queryParam("page", p).queryParam("size", PAGE_SIZE).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Product>>() {
                    }));
            if (current == null || current.content() == null) {
                break;
            }
            for (RemoteResponses.Product product : current.content()) {
                if (result.size() >= max) {
                    return result;
                }
                result.add(toInfo(product));
            }
            page++;
        } while (page < current.totalPages());
        return result;
    }

    @Override
    public Optional<ProductInfo> product(Long productId) {
        try {
            RemoteResponses.Product p = products.get().uri("/api/products/{id}", productId).retrieve()
                    .body(RemoteResponses.Product.class);
            return Optional.ofNullable(p).map(CatalogClient::toInfo);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new RemoteServiceException("product-service", "Erreur product-service (HTTP "
                    + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new RemoteServiceException("product-service", "Catalogue produits indisponible", e);
        }
    }

    @Override
    public List<CatalogSupplier> activeSuppliers() {
        RemoteResponses.Page<RemoteResponses.Supplier> page = call("supplier-service", () -> suppliers.get()
                .uri(uri -> uri.path("/api/suppliers").queryParam("active", true)
                        .queryParam("page", 0).queryParam("size", 500).build())
                .retrieve()
                .body(new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Supplier>>() {
                }));
        if (page == null || page.content() == null) {
            return List.of();
        }
        return page.content().stream()
                .map(s -> new CatalogSupplier(s.id(), s.code(), s.name(), s.email(), s.phone(), s.city(), s.country()))
                .toList();
    }

    private static ProductInfo toInfo(RemoteResponses.Product p) {
        return new ProductInfo(p.id(), p.reference(), p.name(), p.description(), p.barcode(), p.categoryName(),
                p.unit(), p.purchasePrice() == null ? BigDecimal.ZERO : p.purchasePrice(),
                p.minStock() == null ? 0 : p.minStock(), p.supplierId(), p.active() == null || p.active());
    }

    private static <T> T call(String service, java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RestClientException e) {
            throw new RemoteServiceException(service, service + " indisponible", e);
        }
    }
}
