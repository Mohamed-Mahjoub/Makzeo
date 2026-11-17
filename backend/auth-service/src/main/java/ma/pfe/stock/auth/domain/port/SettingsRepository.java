package ma.pfe.stock.auth.domain.port;

import ma.pfe.stock.auth.domain.model.AppSettings;

import java.util.Optional;

/** Port de sortie : persistance des paramètres de l'application. */
public interface SettingsRepository {

    Optional<AppSettings> load();

    AppSettings save(AppSettings settings);
}
