package ma.pfe.stock.auth.application.dto;

import java.time.Instant;

/** Résultat d'une authentification réussie. */
public record AuthResult(String accessToken, String tokenType, Instant expiresAt, UserView user) {
}
