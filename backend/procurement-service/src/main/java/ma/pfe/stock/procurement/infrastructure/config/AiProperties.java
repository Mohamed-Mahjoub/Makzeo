package ma.pfe.stock.procurement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Accès à l'API Claude d'Anthropic (préfixe {@code app.ai}). Sans clé, le module fonctionne sans IA :
 * comparaison, prédictions statistiques et recommandations restent disponibles, la veille web est désactivée.
 *
 * @param apiKey             clé de l'API (secret, jamais dans le code)
 * @param model              modèle utilisé
 * @param maxSearchesPerCall recherches web autorisées par question
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String apiKey,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("claude-sonnet-5") String model,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("5") int maxSearchesPerCall,
        @DefaultValue("150s") Duration timeout) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
