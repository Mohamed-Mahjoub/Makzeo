package ma.pfe.stock.reporting.infrastructure.client;

import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.reporting.application.port.CatalogGateway;
import ma.pfe.stock.reporting.domain.model.ProductData;
import ma.pfe.stock.reporting.infrastructure.config.ReportingProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/** Adaptateur HTTP : lecture paginée du catalogue produits. */
@Component
public class ProductCatalogClient implements CatalogGateway {

    private static final int PAGE_SIZE = 500;

    private final RestClient restClient;
    private final int maxRows;

    public ProductCatalogClient(ReportingProperties properties) {
        this.restClient = RestClientFactory.create(properties.productServiceUrl());
        this.maxRows = properties.maxRows();
    }

    @Override
    public List<ProductData> findAllProducts(Long categoryId, Boolean active) {
        List<ProductData> result = new ArrayList<>();
        int page = 0;
        RemoteResponses.Page<RemoteResponses.Product> current;
        do {
            final int pageIndex = page;
            try {
                current = restClient.get()
                        .uri(uri -> uri.path("/api/products")
                                .queryParam("page", pageIndex)
                                .queryParam("size", PAGE_SIZE)
                                .queryParamIfPresent("categoryId", java.util.Optional.ofNullable(categoryId))
                                .queryParamIfPresent("active", java.util.Optional.ofNullable(active))
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Product>>() {
                        });
            } catch (RestClientException e) {
                throw new RemoteServiceException("product-service", "Catalogue produits indisponible", e);
            }
            if (current == null || current.content() == null) {
                break;
            }
            current.content().forEach(p -> result.add(new ProductData(p.id(), p.reference(), p.name(),
                    p.categoryName(), p.barcode(), p.unit(), p.purchasePrice(), p.salePrice(),
                    p.minStock() == null ? 0 : p.minStock(), p.maxStock(), p.active() == null || p.active())));
            page++;
        } while (page < current.totalPages() && result.size() < maxRows);
        return result;
    }
}
