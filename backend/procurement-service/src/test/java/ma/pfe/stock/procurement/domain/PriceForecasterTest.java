package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.procurement.domain.model.ConfidenceLevel;
import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.service.PriceForecaster;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PriceForecasterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 20);
    private static final PriceForecaster.Settings SETTINGS = new PriceForecaster.Settings(3, 6.0, 0.3);

    private static List<PricePoint> monthly(int months, double start, double monthlyGrowth) {
        List<PricePoint> points = new ArrayList<>();
        LocalDate first = TODAY.minusMonths(months - 1).withDayOfMonth(10);
        for (int i = 0; i < months; i++) {
            double price = start * Math.pow(1 + monthlyGrowth, i);
            points.add(new PricePoint((long) i + 1, 1L, BigDecimal.valueOf(price).setScale(3,
                    java.math.RoundingMode.HALF_UP), first.plusMonths(i), PriceSource.PURCHASE, "F", 10));
        }
        return points;
    }

    @Test
    void regularTrendIsExtrapolatedWithHighConfidence() {
        PriceForecaster.Forecast f = PriceForecaster.forecast(monthly(12, 100, 0.02), TODAY, SETTINGS, null)
                .orElseThrow();
        assertThat(f.changePct().doubleValue()).isCloseTo(6.12, within(0.05));
        assertThat(f.monthlyTrendPct().doubleValue()).isCloseTo(2.0, within(0.01));
        assertThat(f.sampleSize()).isEqualTo(12);
        assertThat(f.confidence()).isEqualTo(0.65);
        assertThat(f.level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(f.horizonDays()).isEqualTo(90);
        assertThat(f.predictedPrice().doubleValue())
                .isCloseTo(f.currentPrice().doubleValue() * 1.0612, within(0.05));
    }

    @Test
    void shortHistoryFallsBackOnInflationWithLowConfidence() {
        PriceForecaster.Forecast f = PriceForecaster.forecast(monthly(1, 50, 0), TODAY, SETTINGS, null).orElseThrow();
        // 6 % par an sur 3 mois
        assertThat(f.changePct().doubleValue()).isCloseTo((Math.pow(1.06, 0.25) - 1) * 100, within(0.01));
        assertThat(f.level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(f.rationale()).contains("trop court");
    }

    @Test
    void marketSignalIsBlendedAccordingToItsWeight() {
        PriceForecaster.Forecast f = PriceForecaster.forecast(monthly(12, 100, 0), TODAY, SETTINGS, 10.0)
                .orElseThrow();
        // prix stable : 70 % x 0 % + 30 % x 10 %
        assertThat(f.changePct().doubleValue()).isCloseTo(3.0, within(0.01));
        assertThat(f.marketSignalPct()).isEqualByComparingTo("10.00");
        assertThat(f.confidence()).isEqualTo(0.75);
    }

    @Test
    void decreasingPricesGiveNegativeChange() {
        PriceForecaster.Forecast f = PriceForecaster.forecast(monthly(12, 100, -0.01), TODAY, SETTINGS, null)
                .orElseThrow();
        assertThat(f.changePct().signum()).isNegative();
    }

    @Test
    void emptyOrFutureHistoryGivesNoForecast() {
        assertThat(PriceForecaster.forecast(List.of(), TODAY, SETTINGS, null)).isEmpty();
        PricePoint future = new PricePoint(1L, 1L, BigDecimal.TEN, TODAY.plusDays(5), PriceSource.QUOTE, null, null);
        assertThat(PriceForecaster.forecast(List.of(future), TODAY, SETTINGS, null)).isEmpty();
    }

    @Test
    void settingsAreValidated() {
        assertThatThrownBy(() -> new PriceForecaster.Settings(0, 5, 0.3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriceForecaster.Settings(3, 5, 1.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
