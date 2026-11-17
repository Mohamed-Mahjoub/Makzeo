package ma.pfe.stock.common.exception;

/** Violation d'une contrainte d'unicité métier (traduite en HTTP 409). */
public class DuplicateResourceException extends DomainException {

    public DuplicateResourceException(String message) {
        super("DUPLICATE", message);
    }
}
