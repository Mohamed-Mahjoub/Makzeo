package ma.pfe.stock.common.exception;

/**
 * Exception racine de toutes les erreurs métier (couche domaine).
 * Pure Java : aucune dépendance vers un framework.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
