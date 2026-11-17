package ma.pfe.stock.common.client;

import ma.pfe.stock.common.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propage le jeton JWT de l'utilisateur courant lors des appels inter-services
 * (le service appelé applique ainsi ses propres règles d'autorisation).
 */
public class BearerTokenRelayInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)) {
            CurrentUser.bearerToken().ifPresent(token -> request.getHeaders().setBearerAuth(token));
        }
        return execution.execute(request, body);
    }
}
