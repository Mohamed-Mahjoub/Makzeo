package ma.pfe.stock.procurement.infrastructure.websearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ma.pfe.stock.procurement.infrastructure.config.WebSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client de l'API Extract de Tavily : récupère le texte des pages des fournisseurs (prix, contacts).
 * Une seule requête par article pour toutes ses pages (au plus 20 URL ; 5 pages lues = 1 crédit).
 */
@Component
public class TavilyClient {

    private static final Logger log = LoggerFactory.getLogger(TavilyClient.class);
    private static final int MAX_CONTENT = 60_000;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Map<String, Object>> results, List<Map<String, Object>> failed_results) {
    }

    private final WebSearchProperties web;
    private final MeterRegistry meters;
    private final RestClient rest;

    public TavilyClient(WebSearchProperties web, MeterRegistry meters) {
        this.web = web;
        this.meters = meters;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(web.timeout().plusSeconds(15));
        RestClient.Builder builder = RestClient.builder().baseUrl(web.tavilyUrl()).requestFactory(factory);
        if (web.tavilyConfigured()) {
            builder.defaultHeader("Authorization", "Bearer " + web.tavilyApiKey());
        }
        this.rest = builder.build();
    }

    public boolean configured() {
        return web.tavilyConfigured();
    }

    /** Texte des pages (URL → contenu). Les pages illisibles sont absentes du résultat. */
    public Map<String, String> extract(List<String> urls) {
        Map<String, String> pages = new LinkedHashMap<>();
        if (!configured() || urls.isEmpty()) {
            return pages;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("urls", urls.subList(0, Math.min(20, urls.size())));
        body.put("extract_depth", "basic");
        body.put("format", "text");
        body.put("timeout", Math.min(60, Math.max(5, web.timeout().toSeconds())));
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        try {
            Response response = rest.post().uri("/extract").contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Response.class);
            outcome = "success";
            if (response != null && response.results() != null) {
                for (Map<String, Object> r : response.results()) {
                    Object url = r.get("url");
                    Object content = r.get("raw_content");
                    if (url != null && content != null) {
                        String text = content.toString();
                        pages.put(url.toString(), text.length() > MAX_CONTENT ? text.substring(0, MAX_CONTENT) : text);
                    }
                }
            }
            if (response != null && response.failed_results() != null && !response.failed_results().isEmpty()) {
                log.debug("Tavily : {} page(s) illisible(s)", response.failed_results().size());
            }
            return pages;
        } catch (RestClientResponseException e) {
            throw new WebSearchException("Tavily : HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new WebSearchException("Tavily injoignable : " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("makzeo_procurement_ai_call")
                    .description("Appels aux services de l'agent (IA, recherche web)")
                    .tag("operation", "tavily_extract")
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }
}
