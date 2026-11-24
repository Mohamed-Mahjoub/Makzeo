package ma.pfe.stock.procurement.infrastructure.ai;

import ma.pfe.stock.procurement.application.port.MarketIntelligence;

import java.time.format.DateTimeFormatter;

/** Modèle de demande de prix utilisé quand l'IA n'est pas configurée ou ne répond pas. */
final class TemplateRfqWriter {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TemplateRfqWriter() {
    }

    static MarketIntelligence.RfqText write(MarketIntelligence.RfqRequest r, String currency) {
        var p = r.product();
        String subject = "Demande de prix - " + p.name() + " (" + r.quantity() + " " + unit(p.unit()) + ")";
        String body = "Madame, Monsieur,\n\n"
                + "Nous souhaitons recevoir votre meilleure offre pour l'article suivant :\n\n"
                + "  - Désignation : " + p.name() + "\n"
                + (p.barcode() != null ? "  - Code-barres (EAN) : " + p.barcode() + "\n" : "")
                + "  - Notre référence : " + p.reference() + "\n"
                + "  - Quantité : " + r.quantity() + " " + unit(p.unit()) + "\n\n"
                + "Merci de nous préciser :\n"
                + "  - le prix unitaire HT en " + currency + " ;\n"
                + "  - la quantité minimale de commande ;\n"
                + "  - la disponibilité et le délai de livraison ;\n"
                + "  - les conditions de paiement et la durée de validité de l'offre.\n\n"
                + "Si l'article exact n'est pas disponible, un article équivalent (caractéristiques et qualité"
                + " comparables) peut nous être proposé.\n\n"
                + "Nous vous remercions de répondre avant le " + r.responseDueDate().format(DATE_FR)
                + " en rappelant la référence indiquée dans l'objet de ce message.\n\n"
                + "Cordialement,\n"
                + "Service achats\n"
                + r.buyerCompany() + "\n"
                + r.buyerEmail();
        return new MarketIntelligence.RfqText(subject, body);
    }

    private static String unit(String unit) {
        return unit == null ? "unité(s)" : unit.toLowerCase();
    }
}
