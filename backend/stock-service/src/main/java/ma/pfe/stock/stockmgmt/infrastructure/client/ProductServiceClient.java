package ma.pfe.stock.stockmgmt.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.infrastructure.config.StockProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Adaptateur HTTP du port ProductCatalog vers product-service. */
@Component
public class ProductServiceClient implements ProductCatalog {

    private static final int BATCH_SIZE = 500;
    private static final String SERVICE = "product-service";

    private final RestClient restClient;

    public ProductServiceClient(StockProperties properties) {
        this.restClient = RestClientFactory.create(properties.productServiceUrl());
    }

    @Override
    public ProductSnapshot getProduct(Long productId) {
        try {
            ProductResponse p = restClient.get().uri("/api/products/{id}", productId)
                    .retrieve().body(ProductResponse.class);
            if (p == null) {
                throw new ResourceNotFoundException("Produit", productId);
            }
            return p.toSnapshot();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Produit", productId);
            }
            throw new RemoteServiceException(SERVICE, "Erreur product-service (HTTP " + e.getStatusCode().value()
                    + ")", e);
        } catch (RestClientException e) {
            throw new RemoteServiceException(SERVICE, "Catalogue produits indisponible", e);
        }
    }

    @Override
    public List<ProductSnapshot> getProducts(Collection<Long> productIds) {
        List<Long> ids = new ArrayList<>(productIds);
        List<ProductSnapshot> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            try {
                List<ProductResponse> page = restClient.get()
                        .uri(uri -> uri.path("/api/products/batch").queryParam("ids", chunk.toArray()).build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<ProductResponse>>() {
                        });
                if (page != null) {
                    page.forEach(p -> result.add(p.toSnapshot()));
                }
            } catch (RestClientException e) {
                throw new RemoteServiceException(SERVICE, "Catalogue produits indisponible", e);
            }
        }
        return result;
    }

    /** Sous-ensemble de la représentation JSON d'un produit. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductResponse(Long id, String reference, String name, Integer minStock, Boolean active) {

        ProductSnapshot toSnapshot() {
            return new ProductSnapshot(id, reference, name, minStock == null ? 0 : minStock,
                    active == null || active);
        }
    }
}
