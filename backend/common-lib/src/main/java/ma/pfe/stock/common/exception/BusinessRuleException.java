package ma.pfe.stock.common.exception;

/** Violation d'une règle de gestion (traduite en HTTP 409). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    public BusinessRuleException(String message) {
        this("BUSINESS_RULE", message);
    }
}
