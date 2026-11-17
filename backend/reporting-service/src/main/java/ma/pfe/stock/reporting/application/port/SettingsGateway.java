package ma.pfe.stock.reporting.application.port;

import ma.pfe.stock.reporting.domain.model.CurrencyInfo;

/** Port : paramètres de l'application (devise), lus depuis auth-service. */
public interface SettingsGateway {

    CurrencyInfo currency();
}
