package ma.pfe.stock.product.infrastructure.client;

import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.product.application.port.SupplierDirectory;
import ma.pfe.stock.product.infrastructure.config.ServicesProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptateur HTTP vers supplier-service (le jeton de l'utilisateur est relayé). */
@Component
public class SupplierServiceClient implements SupplierDirectory {

    private final RestClient restClient;
    private final boolean enabled;

    public SupplierServiceClient(ServicesProperties properties) {
        this.restClient = RestClientFactory.create(properties.supplierUrl());
        this.enabled = properties.supplierCheckEnabled();
    }

    @Override
    public boolean exists(Long supplierId) {
        if (!enabled) {
            return true;
        }
        try {
            restClient.get().uri("/api/suppliers/{id}", supplierId).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            throw new RemoteServiceException("supplier-service",
                    "Vérification du fournisseur impossible (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new RemoteServiceException("supplier-service", "Service fournisseurs indisponible", e);
        }
    }
}
