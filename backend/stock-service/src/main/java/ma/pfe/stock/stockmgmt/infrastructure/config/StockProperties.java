package ma.pfe.stock.stockmgmt.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param productServiceUrl URL de product-service (nom DNS du Service Kubernetes)
 * @param zone              fuseau horaire métier (bornes de dates des recherches et du tableau de bord)
 */
@ConfigurationProperties(prefix = "app")
public record StockProperties(
        @DefaultValue("http://localhost:8082") String productServiceUrl,
        @DefaultValue("Africa/Casablanca") String zone) {
}
