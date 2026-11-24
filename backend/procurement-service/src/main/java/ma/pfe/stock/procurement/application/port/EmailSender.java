package ma.pfe.stock.procurement.application.port;

import java.util.List;

/** Envoi de courriels (demandes de prix, alertes au responsable des achats). */
public interface EmailSender {

    boolean configured();

    /** @throws EmailException si l'envoi échoue */
    void send(List<String> to, String subject, String body, String replyTo);

    class EmailException extends RuntimeException {
        public EmailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
