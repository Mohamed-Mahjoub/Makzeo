package ma.pfe.stock.procurement.infrastructure.websearch;

import ma.pfe.stock.procurement.application.port.MarketIntelligence.ExtractedQuote;
import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteTextParserTest {

    private static final ProductInfo TONER = new ProductInfo(2L, "CE285A", "Toner HP 85A noir original LaserJet (CE285A)",
            null, "2000000000022", "Informatique", "piece", new BigDecimal("326.891"), 4, 1L, true);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void readsALabelledQuote() {
        String text = """
                Bonjour,
                Suite à votre demande RFQ-202611-N87YEC, nous vous proposons :
                Toner HP 85A original
                Prix unitaire HT : 245,500 DT
                Quantité minimale : 10 pièces
                Délai de livraison : 3 jours ouvrables
                Paiement à 30 jours fin de mois
                Offre valable jusqu'au 15/12/2026
                Tunewtec SARL
                """;
        ExtractedQuote q = QuoteTextParser.parse(text, TONER, "TND", TODAY).orElseThrow();
        assertThat(q.unitPrice()).isEqualByComparingTo("245.500");
        assertThat(q.priceIncludesVat()).isFalse();
        assertThat(q.minQuantity()).isEqualTo(10);
        assertThat(q.leadTimeDays()).isEqualTo(3);
        assertThat(q.validUntil()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(q.paymentTerms()).isEqualTo("Paiement à 30 jours fin de mois");
        assertThat(q.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(q.supplierName()).isEqualTo("Tunewtec SARL");
    }

    @Test
    void readsAShortMessageWithVatAndDelayInWeeks() {
        String text = "Toner 85A : 289,000 DT TTC, sur commande, livré sous 2 semaines. Offre valable 15 jours.";
        ExtractedQuote q = QuoteTextParser.parse(text, TONER, "TND", TODAY).orElseThrow();
        assertThat(q.unitPrice()).isEqualByComparingTo("289.000");
        assertThat(q.priceIncludesVat()).isTrue();
        assertThat(q.availability()).isEqualTo(Availability.ON_ORDER);
        assertThat(q.leadTimeDays()).isEqualTo(14);
        assertThat(q.validUntil()).isEqualTo(TODAY.plusDays(15));
    }

    @Test
    void returnsNothingWithoutAPlausiblePrice() {
        assertThat(QuoteTextParser.parse("Merci pour votre demande, nous revenons vers vous.", TONER, "TND", TODAY)).isEmpty();
    }
}
