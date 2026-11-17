package ma.pfe.stock.common.exception;

/** Échec d'authentification (identifiants invalides, compte désactivé) - traduit en HTTP 401. */
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException(String message) {
        super("AUTHENTICATION_FAILED", message);
    }
}
