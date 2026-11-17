package ma.pfe.stock.reporting.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.reporting.application.port.SettingsGateway;
import ma.pfe.stock.reporting.domain.model.CurrencyInfo;
import ma.pfe.stock.reporting.infrastructure.config.ReportingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lit la devise de l'application auprès d'auth-service.
 * En cas d'indisponibilité, la devise par défaut est utilisée : un rapport n'échoue pas pour un libellé.
 */
@Component
public class SettingsClient implements SettingsGateway {

    private static final Logger log = LoggerFactory.getLogger(SettingsClient.class);

    private final RestClient restClient;
    private final CurrencyInfo fallback;

    public SettingsClient(ReportingProperties properties) {
        this.restClient = RestClientFactory.create(properties.authServiceUrl());
        this.fallback = new CurrencyInfo(properties.defaultCurrency(),
                java.util.Currency.getInstance(properties.defaultCurrency()).getDefaultFractionDigits());
    }

    @Override
    public CurrencyInfo currency() {
        try {
            SettingsResponse s = restClient.get().uri("/api/settings").retrieve().body(SettingsResponse.class);
            if (s == null || s.currency() == null) {
                return fallback;
            }
            return new CurrencyInfo(s.currency(), s.currencyFractionDigits() == null ? 2 : s.currencyFractionDigits());
        } catch (RestClientException e) {
            log.warn("Paramètres indisponibles (auth-service) : devise par défaut {} utilisée", fallback.code());
            return fallback;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SettingsResponse(String currency, Integer currencyFractionDigits) {
    }
}
