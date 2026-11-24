package ma.pfe.stock.procurement.infrastructure.mail;

import ma.pfe.stock.procurement.application.port.EmailSender;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Envoi SMTP. Le serveur se configure par variables d'environnement (SPRING_MAIL_HOST, SPRING_MAIL_PORT,
 * SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD). Sans serveur configuré, aucun courriel ne part.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ProcurementProperties properties;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailSender, ProcurementProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        return mailSender.getIfAvailable() != null;
    }

    @Override
    public void send(List<String> to, String subject, String body, String replyTo) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new EmailException("Serveur SMTP non configuré (SPRING_MAIL_HOST)", null);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.buyerEmail());
        message.setTo(to.toArray(String[]::new));
        message.setSubject(subject);
        message.setText(body);
        message.setReplyTo(replyTo != null ? replyTo : properties.buyerEmail());
        try {
            sender.send(message);
            log.info("Courriel envoyé à {} : {}", to, subject);
        } catch (MailException e) {
            throw new EmailException("Échec de l'envoi SMTP : " + e.getMessage(), e);
        }
    }
}
