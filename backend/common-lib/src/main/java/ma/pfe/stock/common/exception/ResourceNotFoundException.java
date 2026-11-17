package ma.pfe.stock.common.exception;

/** Ressource introuvable (traduite en HTTP 404). */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " introuvable : " + id);
    }
}
