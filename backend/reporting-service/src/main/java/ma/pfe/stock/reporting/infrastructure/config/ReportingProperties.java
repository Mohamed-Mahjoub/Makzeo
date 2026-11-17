package ma.pfe.stock.reporting.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param productServiceUrl URL de product-service
 * @param stockServiceUrl   URL de stock-service
 * @param authServiceUrl    URL d'auth-service (paramètres : devise)
 * @param defaultCurrency   devise utilisée si auth-service ne répond pas
 * @param zone              fuseau horaire d'affichage des dates
 * @param maxRows           nombre maximum de lignes par rapport (protection mémoire)
 */
@ConfigurationProperties(prefix = "app")
public record ReportingProperties(
        @DefaultValue("http://localhost:8082") String productServiceUrl,
        @DefaultValue("http://localhost:8084") String stockServiceUrl,
        @DefaultValue("http://localhost:8081") String authServiceUrl,
        @DefaultValue("MAD") String defaultCurrency,
        @DefaultValue("Africa/Casablanca") String zone,
        @DefaultValue("20000") int maxRows) {
}
