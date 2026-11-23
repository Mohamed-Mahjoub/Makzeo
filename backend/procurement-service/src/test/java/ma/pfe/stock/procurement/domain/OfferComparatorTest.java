package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.Quote;
import ma.pfe.stock.procurement.domain.model.QuoteSource;
import ma.pfe.stock.procurement.domain.service.OfferComparator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferComparatorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static Quote quote(long id, String supplier, String price, int minQty, Integer lead, int quality,
                               LocalDate validUntil, Availability availability) {
        return new Quote(id, null, 1L, id, supplier, "Article", Equivalence.IDENTICAL, new BigDecimal(price), "TND",
                minQty, availability, lead, null, validUntil, quality, QuoteSource.MANUAL,
                Instant.parse("2026-11-15T10:00:00Z"));
    }

    @Test
    void ranksOffersByValueForMoneyAndExcludesExpiredOnes() {
        LocalDate later = TODAY.plusDays(30);
        List<Quote> quotes = List.of(
                quote(1, "A", "100", 1, 2, 4, later, Availability.IN_STOCK),
                quote(2, "B", "90", 1, 10, 3, later, Availability.IN_STOCK),
                quote(3, "C", "80", 1, 1, 5, TODAY.minusDays(1), Availability.IN_STOCK),
                quote(4, "D", "85", 50, 5, 5, later, Availability.IN_STOCK));

        List<OfferComparator.RankedOffer> ranked = OfferComparator.compare(quotes, 20, new BigDecimal("110"), TODAY,
                UTC, OfferComparator.Weights.DEFAULT);

        assertThat(ranked).extracting(r -> r.quote().supplierName()).containsExactly("A", "B", "D", "C");
        assertThat(ranked).extracting(OfferComparator.RankedOffer::rank).containsExactly(1, 2, 3, 0);
        assertThat(ranked.get(0).score()).isEqualTo(90.0);
        assertThat(ranked.get(1).score()).isEqualTo(77.5);
        // le minimum de commande de D (50 pour un besoin de 20) renchérit son coût réel
        assertThat(ranked.get(2).orderQuantity()).isEqualTo(50);
        assertThat(ranked.get(2).effectiveUnitCost()).isEqualByComparingTo("212.5");
        assertThat(ranked.get(3).eligible()).isFalse();
        assertThat(ranked.get(3).exclusionReason()).isEqualTo("Offre expirée");
        assertThat(ranked.get(0).unitSaving()).isEqualByComparingTo("10");
        assertThat(ranked.get(0).savingPct()).isEqualByComparingTo("9.09");
    }

    @Test
    void unavailableOffersAreNotEligible() {
        List<OfferComparator.RankedOffer> ranked = OfferComparator.compare(
                List.of(quote(1, "A", "50", 1, 3, 3, null, Availability.UNAVAILABLE)), 5, null, TODAY, UTC, null);
        assertThat(ranked.get(0).eligible()).isFalse();
        assertThat(ranked.get(0).unitSaving()).isNull();
    }

    @Test
    void offerWithoutValidityIsValidThirtyDaysAfterReception() {
        Quote q = quote(1, "A", "50", 1, 3, 3, null, Availability.IN_STOCK);
        assertThat(q.isValidOn(LocalDate.of(2026, 12, 15), UTC)).isTrue();
        assertThat(q.isValidOn(LocalDate.of(2026, 12, 16), UTC)).isFalse();
    }

    @Test
    void weightsAreNormalizedAndValidated() {
        OfferComparator.Weights w = new OfferComparator.Weights(3, 1, 1);
        assertThat(w.price()).isEqualTo(0.6);
        assertThatThrownBy(() -> new OfferComparator.Weights(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OfferComparator.compare(List.of(), 0, null, TODAY, UTC, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
