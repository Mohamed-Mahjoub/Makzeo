package ma.pfe.stock.product.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** URLs des microservices appelés (noms DNS des Services Kubernetes). */
@ConfigurationProperties(prefix = "app.services")
public record ServicesProperties(
        @DefaultValue("http://localhost:8083") String supplierUrl,
        @DefaultValue("true") boolean supplierCheckEnabled) {
}
