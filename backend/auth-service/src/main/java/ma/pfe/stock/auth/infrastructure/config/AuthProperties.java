package ma.pfe.stock.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Paramètres propres à auth-service (préfixe {@code app.auth}).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("8h") Duration tokenValidity,
        @DefaultValue Admin admin) {

    /** Compte administrateur créé au premier démarrage si la base est vide. */
    public record Admin(
            @DefaultValue("admin") String username,
            @DefaultValue("admin@stock.local") String email,
            @DefaultValue("Administrateur") String fullName,
            String password) {
    }
}
