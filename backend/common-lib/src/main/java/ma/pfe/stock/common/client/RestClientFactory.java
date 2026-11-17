package ma.pfe.stock.common.client;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fabrique de {@link RestClient} pour la communication synchrone entre microservices.
 * Dans Kubernetes, l'URL de base est le nom DNS du Service (ex : http://product-service:8082).
 */
public final class RestClientFactory {

    private RestClientFactory() {
    }

    public static RestClient create(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new BearerTokenRelayInterceptor())
                .build();
    }

    public static RestClient create(String baseUrl) {
        return create(baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }
}
