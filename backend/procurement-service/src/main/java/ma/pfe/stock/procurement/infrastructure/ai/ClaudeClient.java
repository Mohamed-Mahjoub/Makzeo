package ma.pfe.stock.procurement.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import ma.pfe.stock.procurement.infrastructure.config.AiProperties;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client minimal de l'API Messages d'Anthropic, utilisé en mode "appel d'outil" : le modèle doit rendre
 * son résultat en appelant un outil dont le schéma JSON est imposé, ce qui garantit une réponse structurée.
 * La recherche web est un outil côté serveur d'Anthropic ; ses résultats arrivent dans la même réponse.
 */
@Component
public class ClaudeClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TURNS = 6;

    /** Outil de restitution du résultat. */
    public record ToolSpec(String name, String description, Map<String, Object> inputSchema) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MessagesResponse(String id, List<Map<String, Object>> content, String stop_reason,
                            Map<String, Object> usage) {
    }

    public static class AiException extends RuntimeException {
        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final AiProperties ai;
    private final ProcurementProperties procurement;
    private final RestClient rest;
    private final MeterRegistry meters;

    public ClaudeClient(AiProperties ai, ProcurementProperties procurement, MeterRegistry meters) {
        this.ai = ai;
        this.procurement = procurement;
        this.meters = meters;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(ai.timeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(ai.baseUrl())
                .requestFactory(factory)
                .defaultHeader("anthropic-version", API_VERSION);
        if (ai.configured()) {
            builder.defaultHeader("x-api-key", ai.apiKey());
        }
        this.rest = builder.build();
    }

    public boolean configured() {
        return ai.configured();
    }

    /**
     * Pose une question et renvoie les arguments de l'outil de restitution.
     *
     * @param operation nom de l'opération (métriques)
     * @param webSearch autorise la recherche web (marché local)
     */
    public Map<String, Object> callTool(String operation, String system, String prompt, ToolSpec tool,
                                        boolean webSearch) {
        if (!configured()) {
            throw new AiException("Clé de l'API Claude non configurée (AI_API_KEY)", null);
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        boolean forceTool = !webSearch;
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        try {
            for (int turn = 0; turn < MAX_TURNS; turn++) {
                MessagesResponse response = send(system, messages, tool, webSearch, forceTool);
                List<Map<String, Object>> content = response.content() == null ? List.of() : response.content();
                for (Map<String, Object> block : content) {
                    if ("tool_use".equals(block.get("type")) && tool.name().equals(block.get("name"))) {
                        Object input = block.get("input");
                        if (input instanceof Map<?, ?> map) {
                            outcome = "success";
                            @SuppressWarnings("unchecked")
                            Map<String, Object> typed = (Map<String, Object>) map;
                            return typed;
                        }
                    }
                }
                messages.add(Map.of("role", "assistant", "content", content));
                if (!"pause_turn".equals(response.stop_reason())) {
                    // le modèle a répondu en texte : on lui demande de restituer via l'outil
                    messages.add(Map.of("role", "user", "content",
                            "Enregistre maintenant ton résultat en appelant l'outil " + tool.name()
                                    + ". N'invente aucune donnée : laisse vide ce que tu n'as pas trouvé."));
                    forceTool = true;
                }
            }
            throw new AiException("L'agent n'a pas restitué de résultat structuré", null);
        } finally {
            sample.stop(Timer.builder("makzeo_procurement_ai_call")
                    .description("Appels à l'API Claude")
                    .tag("operation", operation)
                    .tag("outcome", outcome)
                    .register(meters));
        }
    }

    private MessagesResponse send(String system, List<Map<String, Object>> messages, ToolSpec tool,
                                  boolean webSearch, boolean forceTool) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (webSearch) {
            Map<String, Object> search = new LinkedHashMap<>();
            search.put("type", "web_search_20250305");
            search.put("name", "web_search");
            search.put("max_uses", ai.maxSearchesPerCall());
            search.put("user_location", Map.of("type", "approximate", "country", procurement.marketCountry(),
                    "timezone", procurement.zone()));
            tools.add(search);
        }
        tools.add(Map.of("name", tool.name(), "description", tool.description(), "input_schema", tool.inputSchema()));

        Map<String, Object> body = new HashMap<>();
        body.put("model", ai.model());
        body.put("max_tokens", ai.maxTokens());
        body.put("system", system);
        body.put("messages", messages);
        body.put("tools", tools);
        body.put("tool_choice", forceTool ? Map.of("type", "tool", "name", tool.name()) : Map.of("type", "auto"));
        try {
            MessagesResponse response = rest.post().uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MessagesResponse.class);
            if (response == null) {
                throw new AiException("Réponse vide de l'API Claude", null);
            }
            if (response.usage() != null) {
                log.debug("API Claude : {} jetons en entrée, {} en sortie", response.usage().get("input_tokens"),
                        response.usage().get("output_tokens"));
            }
            return response;
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            throw new AiException("API Claude : HTTP " + e.getStatusCode().value() + " "
                    + (detail.length() > 300 ? detail.substring(0, 300) : detail), e);
        } catch (RestClientException e) {
            throw new AiException("API Claude injoignable : " + e.getMessage(), e);
        }
    }
}
