package ma.pfe.stock.common.exception;

/** Un microservice distant est indisponible ou a répondu en erreur (traduite en HTTP 503). */
public class RemoteServiceException extends RuntimeException {

    private final String service;

    public RemoteServiceException(String service, String message, Throwable cause) {
        super(message, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
