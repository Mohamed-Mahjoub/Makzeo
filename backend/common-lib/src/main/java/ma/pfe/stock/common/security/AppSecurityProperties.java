package ma.pfe.stock.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Paramètres de sécurité partagés par tous les microservices (préfixe {@code app.security}).
 *
 * @param jwtSecret      clé HMAC partagée (au moins 32 caractères) - injectée via un Secret Kubernetes
 * @param jwtIssuer      émetteur attendu des jetons
 * @param publicPaths    chemins accessibles sans authentification
 * @param allowedOrigins origines CORS autorisées (frontend Angular)
 */
@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        String jwtSecret,
        @DefaultValue("stock-management-auth") String jwtIssuer,
        @DefaultValue List<String> publicPaths,
        @DefaultValue("http://localhost:4200") List<String> allowedOrigins) {

    public AppSecurityProperties {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "app.security.jwt-secret doit être défini et contenir au moins 32 caractères");
        }
    }
}
