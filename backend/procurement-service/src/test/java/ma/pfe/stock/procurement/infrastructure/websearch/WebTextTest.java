package ma.pfe.stock.procurement.infrastructure.websearch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebTextTest {

    @Test
    void readsTunisianAmounts() {
        assertThat(WebText.parseAmount("289,000 DT")).contains(new BigDecimal("289.000"));
        assertThat(WebText.parseAmount("1 299,000")).contains(new BigDecimal("1299.000"));
        assertThat(WebText.parseAmount("1.299,000")).contains(new BigDecimal("1299.000"));
        assertThat(WebText.parseAmount("45.900")).contains(new BigDecimal("45.900"));
        assertThat(WebText.parseAmount("1,299.50")).contains(new BigDecimal("1299.500"));
        assertThat(WebText.parseAmount("prix sur demande")).isEmpty();
    }

    @Test
    void findsPricesAndVat() {
        List<WebText.Price> prices = WebText.prices("Toner HP 85A : 377,000 DT TTC - version pro 242,017 TND HT");
        assertThat(prices).extracting(WebText.Price::amount)
                .containsExactly(new BigDecimal("377.000"), new BigDecimal("242.017"));
        assertThat(prices.get(0).includesVat()).isTrue();
        assertThat(prices.get(1).includesVat()).isFalse();
    }

    @Test
    void productPriceFollowsTheProductAndStaysPlausible() {
        String page = "Promo du jour : clé USB 19,900 DT. Fiche produit : Toner HP CE285A noir. Prix : 288,000 DT";
        assertThat(WebText.productPrice(page, List.of("CE285A"), new BigDecimal("326.891")))
                .map(WebText.Price::amount).contains(new BigDecimal("288.000"));
        assertThat(WebText.productPrice("Accessoire 12,000 DT", List.of("CE285A"), new BigDecimal("326.891"))).isEmpty();
    }

    @Test
    void readsContacts() {
        String page = "Contact : info@gmail.com ou ventes@tunewtec.tn - Tél : +216 71 234 567 - Adresse : Route de Gabès, Sfax";
        assertThat(WebText.email(page, "tunewtec.tn")).isEqualTo("ventes@tunewtec.tn");
        assertThat(WebText.phone(page)).isEqualTo("+216 71 234 567");
        assertThat(WebText.city("Showroom à Sfax")).isEqualTo("Sfax");
        assertThat(WebText.phone("EAN 2000000000022")).isNull();
    }

    @Test
    void comparesDesignations() {
        String ours = "Toner HP 85A noir original LaserJet (CE285A)";
        assertThat(WebText.similarity(ours, "Toner compatible HP 85A noir")).isEqualTo(60);
        assertThat(WebText.containsCode("Toner HP CE-285A", "CE285A")).isTrue();
        assertThat(WebText.containsCode("Toner HP", "TONER")).isFalse();
        assertThat(WebText.nameFromHost("tunisianet.com.tn")).isEqualTo("Tunisianet");
        assertThat(WebText.host("https://www.mytek.tn/toner.html")).isEqualTo("mytek.tn");
    }
}
