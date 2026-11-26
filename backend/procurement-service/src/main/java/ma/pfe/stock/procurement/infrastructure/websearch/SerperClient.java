package ma.pfe.stock.procurement.infrastructure.websearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import ma.pfe.stock.procurement.infrastructure.config.WebSearchProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client de l'API Serper (résultats Google). Deux recherches : Google Shopping, qui donne des offres avec
 * leur prix et leur marchand, et la recherche web classique, qui donne les pages des fournisseurs.
 * Les deux sont limitées au pays du marché ({@code gl}).
 */
@Component
public class SerperClient {

    /** Résultat utile d'une recherche. {@code priceText} n'est rempli que pour Google Shopping. */
    public record Hit(String title, String link, String snippet, String source, String priceText) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Map<String, Object>> organic, List<Map<String, Object>> shopping) {
    }

    private final WebSearchProperties web;
    private final ProcurementProperties procurement;
    private final MeterRegistry meters;
    private final RestClient rest;

    public SerperClient(WebSearchProperties web, ProcurementProperties procurement, MeterRegistry meters) {
        this.web = web;
        this.procurement = procurement;
        this.meters = meters;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(web.timeout());
        RestClient.Builder builder = RestClient.builder().baseUrl(web.serperUrl()).requestFactory(factory);
        if (web.configured()) {
            builder.defaultHeader("X-API-KEY", web.serperApiKey());
        }
        this.rest = builder.build();
    }

    public boolean configured() {
        return web.configured();
    }

    /** Offres Google Shopping du pays du marché. */
    public List<Hit> shopping(String query) {
        Response r = call("/shopping", "serper_shopping", query);
        return hits(r == null ? null : r.shopping());
    }

    /** Pages web du pays du marché. */
    public List<Hit> search(String query) {
        Response r = call("/search", "serper_search", query);
        return hits(r == null ? null : r.organic());
    }

    private Response call(String path, String operation, String query) {
        if (!configured()) {
            throw new WebSearchException("Clé Serper non configurée (SERPER_API_KEY)", null);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("gl", procurement.marketCountry().toLowerCase(Locale.ROOT));
        body.put("hl", web.language());
        body.put("num", Math.max(1, Math.min(100, web.resultsPerQuery())));
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        try {
            Response response = rest.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Response.class);
            outcome = "success";
            return response;
        } catch (RestClientResponseException e) {
            throw new WebSearchException("Serper : HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new WebSearchException("Serper injoignable : " + e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("makzeo_procurement_ai_call")
                    .description("Appels aux services de l'agent (IA, recherche web)")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }

    private static List<Hit> hits(List<Map<String, Object>> items) {
        List<Hit> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (Map<String, Object> item : items) {
            String link = text(item.get("link"));
            String title = text(item.get("title"));
            if (link == null || title == null || !(link.startsWith("https://") || link.startsWith("http://"))) {
                continue;
            }
            out.add(new Hit(title, link, text(item.get("snippet")), text(item.get("source")), text(item.get("price"))));
        }
        return out;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
