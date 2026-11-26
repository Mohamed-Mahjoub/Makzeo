package ma.pfe.stock.procurement.infrastructure.websearch;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ma.pfe.stock.procurement.application.port.MarketIntelligence.SupplierFinding;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import ma.pfe.stock.procurement.infrastructure.config.WebSearchProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSearchMarketIntelligenceTest {

    private static final WebSearchProperties WEB = new WebSearchProperties("serper-key", "tavily-key",
            "http://serper.invalid", "http://tavily.invalid", "fr", 10, 5, 50, List.of("facebook.com", "primini.tn"),
            Duration.ofSeconds(5));
    private static final ProcurementProperties PROC = new ProcurementProperties("http://p", "http://s", "http://st",
            "Africa/Tunis", "TND", "TN", "Tunisie", 19, true, "0 30 6 1 * *", 25, 5, 3, 180, 3, 5.5, 0.3, 0.015, 12,
            3, 5, null, 14, "Makzeo", "achats@makzeo.local", List.of(), "http://localhost:8080");
    private static final ProductInfo TONER = new ProductInfo(2L, "CE285A", "Toner HP 85A noir original LaserJet (CE285A)",
            null, "2000000000022", "Informatique", "piece", new BigDecimal("326.891"), 4, 1L, true);

    private static SerperClient serper(List<SerperClient.Hit> shopping, List<SerperClient.Hit> organic, boolean fail) {
        return new SerperClient(WEB, PROC, new SimpleMeterRegistry()) {
            @Override
            public List<Hit> shopping(String query) {
                if (fail) throw new WebSearchException("Serper : HTTP 401", null);
                return shopping;
            }

            @Override
            public List<Hit> search(String query) {
                if (fail) throw new WebSearchException("Serper : HTTP 401", null);
                return organic;
            }
        };
    }

    private static TavilyClient tavily(Map<String, String> pages) {
        return new TavilyClient(WEB, new SimpleMeterRegistry()) {
            @Override
            public Map<String, String> extract(List<String> urls) {
                return pages;
            }
        };
    }

    @Test
    void findsIdenticalAndEquivalentOffersOnTheLocalMarket() {
        var shopping = List.of(
                new SerperClient.Hit("Toner HP 85A CE285A noir", "https://www.tunewtec.tn/toner-ce285a", null, "Tunewtec", "288,000 DT"),
                new SerperClient.Hit("Toner HP 85A", "https://www.facebook.com/boutique", null, "Facebook", "250,000 DT"),
                new SerperClient.Hit("Imprimante HP LaserJet Pro", "https://www.mytek.tn/imprimante", null, "MyTEK", "450,000 DT"));
        var organic = List.of(
                new SerperClient.Hit("Toner compatible HP 85A noir - NUMEDIA", "https://numedia.tn/toner-85a",
                        "Toner compatible HP 85A noir, 320,000 DT, livraison partout en Tunisie", null, null));
        var pages = Map.of("https://www.tunewtec.tn/toner-ce285a",
                "Toner HP CE285A ... Contact : ventes@tunewtec.tn - Tél : 71 234 567 - Sfax");

        var agent = new WebSearchMarketIntelligence(serper(shopping, organic, false), tavily(pages), WEB, PROC);
        List<SupplierFinding> found = agent.findSuppliers(TONER, 5);

        assertThat(agent.enabled()).isTrue();
        assertThat(agent.provider()).isEqualTo("Serper + Tavily");
        assertThat(found).extracting(SupplierFinding::name).containsExactly("Tunewtec", "Numedia");

        SupplierFinding tunewtec = found.get(0);
        assertThat(tunewtec.equivalence()).isEqualTo(Equivalence.IDENTICAL);
        assertThat(tunewtec.price()).isEqualByComparingTo("288.000");
        assertThat(tunewtec.priceIncludesVat()).isTrue();
        assertThat(tunewtec.email()).isEqualTo("ventes@tunewtec.tn");
        assertThat(tunewtec.phone()).isEqualTo("+216 71 234 567");
        assertThat(tunewtec.city()).isEqualTo("Sfax");
        assertThat(tunewtec.website()).isEqualTo("https://tunewtec.tn");

        SupplierFinding numedia = found.get(1);
        assertThat(numedia.equivalence()).isEqualTo(Equivalence.EQUIVALENT);
        assertThat(numedia.similarity()).isEqualTo(60);
        assertThat(numedia.price()).isEqualByComparingTo("320.000");
    }

    @Test
    void reportsSerperFailure() {
        var agent = new WebSearchMarketIntelligence(serper(List.of(), List.of(), true), tavily(Map.of()), WEB, PROC);
        assertThatThrownBy(() -> agent.findSuppliers(TONER, 5)).isInstanceOf(WebSearchException.class);
    }

    @Test
    void disabledWithoutSerperKey() {
        var noKey = new WebSearchProperties(null, null, "http://s", "http://t", "fr", 10, 5, 50, List.of(), Duration.ofSeconds(5));
        var agent = new WebSearchMarketIntelligence(new SerperClient(noKey, PROC, new SimpleMeterRegistry()),
                new TavilyClient(noKey, new SimpleMeterRegistry()), noKey, PROC);
        assertThat(agent.enabled()).isFalse();
        assertThat(agent.findSuppliers(TONER, 5)).isEmpty();
    }
}
