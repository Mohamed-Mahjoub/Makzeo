package ma.pfe.stock.procurement.domain.service;

import ma.pfe.stock.procurement.domain.model.ConfidenceLevel;
import ma.pfe.stock.procurement.domain.model.PricePoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Prévision de l'évolution d'un prix, calculée de façon déterministe et explicable :
 * <ol>
 *   <li>moyenne mensuelle des prix observés (achats, devis, prix publiés, prix du catalogue) ;</li>
 *   <li>tendance : régression linéaire du logarithme du prix sur le temps (croissance mensuelle) ;</li>
 *   <li>repli sur l'inflation quand l'historique est court ou peu régulier (pondération par le R² et la
 *       longueur de l'historique) ;</li>
 *   <li>saisonnalité : indice mensuel calculé dès 24 mois d'historique ;</li>
 *   <li>signal de marché optionnel fourni par l'agent IA (matières premières, annonces de hausse),
 *       mélangé selon un poids configurable.</li>
 * </ol>
 * La confiance dépend de la qualité de l'ajustement, de la longueur de l'historique et de la présence
 * d'un signal de marché.
 */
public final class PriceForecaster {

    /** Paramètres de la prévision. */
    public record Settings(int horizonMonths, double annualInflationPct, double marketSignalWeight) {

        public Settings {
            if (horizonMonths < 1 || horizonMonths > 24) {
                throw new IllegalArgumentException("L'horizon doit être compris entre 1 et 24 mois");
            }
            if (marketSignalWeight < 0 || marketSignalWeight > 1) {
                throw new IllegalArgumentException("Le poids du signal de marché doit être compris entre 0 et 1");
            }
        }
    }

    /** Résultat d'une prévision. */
    public record Forecast(BigDecimal currentPrice, BigDecimal predictedPrice, BigDecimal changePct, int horizonDays,
                           double confidence, ConfidenceLevel level, BigDecimal monthlyTrendPct,
                           BigDecimal seasonalityPct, BigDecimal inflationPct, BigDecimal marketSignalPct,
                           int sampleSize, String rationale) {
    }

    private PriceForecaster() {
    }

    public static Optional<Forecast> forecast(List<PricePoint> history, LocalDate today, Settings settings,
                                              Double marketSignalPct) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        List<PricePoint> sorted = history.stream()
                .filter(p -> !p.observedOn().isAfter(today))
                .sorted(Comparator.comparing(PricePoint::observedOn).thenComparingLong(p -> p.id() == null ? 0L : p.id()))
                .toList();
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        double current = sorted.get(sorted.size() - 1).unitPrice().doubleValue();

        // 1. moyennes mensuelles
        Map<YearMonth, double[]> sums = new TreeMap<>();
        for (PricePoint p : sorted) {
            double[] s = sums.computeIfAbsent(YearMonth.from(p.observedOn()), k -> new double[2]);
            s[0] += p.unitPrice().doubleValue();
            s[1]++;
        }
        List<YearMonth> months = new ArrayList<>(sums.keySet());
        int n = months.size();
        YearMonth first = months.get(0);
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            double[] s = sums.get(months.get(i));
            x[i] = ChronoUnit.MONTHS.between(first, months.get(i));
            y[i] = Math.log(s[0] / s[1]);
        }

        // 2. tendance (croissance mensuelle) et qualité de l'ajustement
        double inflationMonthly = Math.log(1 + settings.annualInflationPct() / 100) / 12;
        double slope = inflationMonthly;
        double intercept = y[0];
        double r2 = 0;
        if (n >= 3) {
            double[] fit = regression(x, y);
            slope = fit[0];
            intercept = fit[1];
            r2 = fit[2];
        }
        double historyWeight = clamp(r2 * Math.min(1.0, n / 12.0), 0, 1);
        double blendedSlope = historyWeight * slope + (1 - historyWeight) * inflationMonthly;

        // 3. saisonnalité (au moins deux cycles complets)
        double seasonal = 1.0;
        if (n >= 24) {
            Map<Month, double[]> ratios = new EnumMap<>(Month.class);
            for (int i = 0; i < n; i++) {
                double fitted = intercept + slope * x[i];
                double[] r = ratios.computeIfAbsent(months.get(i).getMonth(), k -> new double[2]);
                r[0] += Math.exp(y[i] - fitted);
                r[1]++;
            }
            Month currentMonth = YearMonth.from(today).getMonth();
            Month targetMonth = YearMonth.from(today).plusMonths(settings.horizonMonths()).getMonth();
            double[] cur = ratios.get(currentMonth);
            double[] tgt = ratios.get(targetMonth);
            if (cur != null && tgt != null) {
                seasonal = (tgt[0] / tgt[1]) / (cur[0] / cur[1]);
            }
        }

        // 4. variation attendue, éventuellement corrigée par le signal de marché
        double baseChange = Math.exp(blendedSlope * settings.horizonMonths()) * seasonal - 1;
        double change = baseChange;
        if (marketSignalPct != null) {
            double signal = clamp(marketSignalPct / 100, -0.5, 1.0);
            change = (1 - settings.marketSignalWeight()) * baseChange + settings.marketSignalWeight() * signal;
        }
        double predicted = current * (1 + change);

        // 5. confiance
        double confidence = 0.2 + 0.45 * r2 * Math.min(1.0, n / 12.0) + (n >= 24 ? 0.15 : 0)
                + (marketSignalPct != null ? 0.1 : 0);
        if (n < 3) {
            confidence = Math.min(confidence, 0.3);
        }
        confidence = Math.round(clamp(confidence, 0.1, 0.9) * 100) / 100.0;
        ConfidenceLevel level = ConfidenceLevel.of(confidence);

        double monthlyTrendPct = (Math.exp(blendedSlope) - 1) * 100;
        String rationale = rationale(n, r2, monthlyTrendPct, settings.annualInflationPct(), seasonal, marketSignalPct,
                change * 100, settings.horizonMonths());

        return Optional.of(new Forecast(
                money(current), money(predicted), pct(change * 100), settings.horizonMonths() * 30, confidence, level,
                pct(monthlyTrendPct), pct((seasonal - 1) * 100), pct(settings.annualInflationPct()),
                marketSignalPct == null ? null : pct(marketSignalPct), n, rationale));
    }

    /** Régression linéaire simple : renvoie {pente, ordonnée à l'origine, R²}. */
    static double[] regression(double[] x, double[] y) {
        int n = x.length;
        double mx = 0;
        double my = 0;
        for (int i = 0; i < n; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (int i = 0; i < n; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        if (sxx == 0) {
            return new double[]{0, my, 0};
        }
        double slope = sxy / sxx;
        double intercept = my - slope * mx;
        // prix parfaitement stables : ajustement parfait (évite une division par un quasi-zéro)
        double r2 = syy < 1e-12 ? 1.0 : (sxy * sxy) / (sxx * syy);
        return new double[]{slope, intercept, r2};
    }

    private static String rationale(int n, double r2, double monthlyTrendPct, double inflation, double seasonal,
                                    Double signal, double changePct, int horizon) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.FRANCE, "Historique de %d mois", n));
        if (n >= 3) {
            sb.append(String.format(java.util.Locale.FRANCE, " (ajustement R² = %.2f)", r2));
        } else {
            sb.append(" : trop court pour une tendance propre, l'inflation sert de base");
        }
        sb.append(String.format(java.util.Locale.FRANCE,
                ". Tendance retenue : %+.2f %% par mois (inflation de référence %.1f %% par an).",
                monthlyTrendPct, inflation));
        if (seasonal != 1.0) {
            sb.append(String.format(java.util.Locale.FRANCE, " Effet saisonnier : %+.1f %%.", (seasonal - 1) * 100));
        }
        if (signal != null) {
            sb.append(String.format(java.util.Locale.FRANCE, " Signal du marché relevé par l'agent : %+.1f %%.", signal));
        }
        sb.append(String.format(java.util.Locale.FRANCE, " Variation attendue sur %d mois : %+.1f %%.", horizon,
                changePct));
        return sb.toString();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
