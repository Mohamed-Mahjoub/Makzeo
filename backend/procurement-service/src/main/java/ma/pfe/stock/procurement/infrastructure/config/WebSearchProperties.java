package ma.pfe.stock.procurement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Veille web sans modèle de langage (préfixe {@code app.websearch}) : Serper interroge Google (résultats
 * Shopping et pages web du pays du marché), Tavily lit les pages des fournisseurs trouvées.
 * Utilisée quand la clé de l'API Claude n'est pas fournie.
 *
 * @param serperApiKey       clé Serper (secret) : active la veille web
 * @param tavilyApiKey       clé Tavily (secret, facultative) : lecture des pages pour les prix et contacts
 * @param language           langue des résultats
 * @param resultsPerQuery    résultats demandés à Serper par requête
 * @param maxPagesPerProduct pages lues avec Tavily par article (5 pages = 1 crédit Tavily)
 * @param minSimilarity      similarité minimale (0-100) pour retenir un article équivalent
 * @param excludedDomains    domaines ignorés : réseaux sociaux, comparateurs de prix, annuaires
 */
@ConfigurationProperties(prefix = "app.websearch")
public record WebSearchProperties(
        String serperApiKey,
        String tavilyApiKey,
        @DefaultValue("https://google.serper.dev") String serperUrl,
        @DefaultValue("https://api.tavily.com") String tavilyUrl,
        @DefaultValue("fr") String language,
        @DefaultValue("10") int resultsPerQuery,
        @DefaultValue("5") int maxPagesPerProduct,
        @DefaultValue("50") int minSimilarity,
        @DefaultValue({"facebook.com", "instagram.com", "youtube.com", "tiktok.com", "linkedin.com", "x.com",
                "twitter.com", "pinterest.com", "wikipedia.org", "primini.tn", "prix.tn", "google.com",
                "amazon.com", "aliexpress.com", "ebay.com"}) List<String> excludedDomains,
        @DefaultValue("30s") Duration timeout) {

    public boolean configured() {
        return serperApiKey != null && !serperApiKey.isBlank();
    }

    public boolean tavilyConfigured() {
        return tavilyApiKey != null && !tavilyApiKey.isBlank();
    }
}
