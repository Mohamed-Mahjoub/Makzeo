package ma.pfe.stock.auth.application.port;

import ma.pfe.stock.auth.domain.model.User;

import java.time.Instant;

/** Port : génération des jetons d'accès (implémenté avec JWT). */
public interface TokenProvider {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
