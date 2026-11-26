package ma.pfe.stock.procurement.infrastructure.websearch;

/** Échec d'un appel à Serper ou à Tavily. */
public class WebSearchException extends RuntimeException {

    public WebSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
