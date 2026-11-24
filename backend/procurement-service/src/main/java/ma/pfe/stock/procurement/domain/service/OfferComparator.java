package ma.pfe.stock.procurement.domain.service;

import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Compare des offres selon le rapport qualité-prix et le besoin de l'entreprise.
 * <p>
 * Score sur 100 = poids prix x (coût effectif le plus bas / coût effectif de l'offre)
 * + poids délai x (délai le plus court + 1) / (délai + 1)
 * + poids qualité x (note / 5).
 * Le coût effectif tient compte de la quantité minimale de commande : acheter 50 unités pour un besoin
 * de 20 renchérit le coût unitaire réel. Un article équivalent (et non identique) perd 5 % de score.
 */
public final class OfferComparator {

    /** Pondérations des critères (normalisées pour totaliser 1). */
    public record Weights(double price, double leadTime, double quality) {

        public static final Weights DEFAULT = new Weights(0.6, 0.2, 0.2);

        public Weights {
            if (price < 0 || leadTime < 0 || quality < 0 || price + leadTime + quality == 0) {
                throw new IllegalArgumentException("Pondérations invalides");
            }
            double sum = price + leadTime + quality;
            price = price / sum;
            leadTime = leadTime / sum;
            quality = quality / sum;
        }
    }

    /**
     * Offre classée.
     *
     * @param orderQuantity     quantité à commander (besoin, ou minimum de commande s'il est supérieur)
     * @param effectiveUnitCost coût total rapporté à la quantité réellement nécessaire
     * @param unitSaving        écart avec le prix de référence (positif : moins cher)
     */
    public record RankedOffer(Quote quote, int rank, boolean eligible, String exclusionReason, int orderQuantity,
                              BigDecimal totalCost, BigDecimal effectiveUnitCost, double score,
                              BigDecimal unitSaving, BigDecimal savingPct) {
    }

    private static final double EQUIVALENT_PENALTY = 0.95;

    private OfferComparator() {
    }

    public static List<RankedOffer> compare(List<Quote> quotes, int neededQuantity, BigDecimal referencePrice,
                                            LocalDate today, ZoneId zone, Weights weights) {
        if (neededQuantity <= 0) {
            throw new IllegalArgumentException("La quantité nécessaire doit être positive");
        }
        Weights w = weights != null ? weights : Weights.DEFAULT;

        record Candidate(Quote quote, String exclusion, int orderQty, BigDecimal total, BigDecimal effective) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Quote q : quotes) {
            String exclusion = null;
            if (!q.isValidOn(today, zone)) {
                exclusion = "Offre expirée";
            } else if (q.availability() == Availability.UNAVAILABLE) {
                exclusion = "Article indisponible chez ce fournisseur";
            }
            int orderQty = Math.max(neededQuantity, q.minQuantity());
            BigDecimal total = q.unitPrice().multiply(BigDecimal.valueOf(orderQty));
            BigDecimal effective = total.divide(BigDecimal.valueOf(neededQuantity), 3, RoundingMode.HALF_UP);
            candidates.add(new Candidate(q, exclusion, orderQty, total.setScale(3, RoundingMode.HALF_UP), effective));
        }

        List<Candidate> eligible = candidates.stream().filter(c -> c.exclusion() == null).toList();
        BigDecimal minCost = eligible.stream().map(Candidate::effective).min(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);
        int minLead = eligible.stream().map(c -> c.quote().leadTimeDays()).filter(d -> d != null)
                .min(Integer::compare).orElse(0);

        List<RankedOffer> ranked = new ArrayList<>();
        for (Candidate c : candidates) {
            double score = 0;
            if (c.exclusion() == null) {
                double priceScore = minCost.doubleValue() / c.effective().doubleValue();
                // délai inconnu : on retient un délai prudent de 30 jours
                int lead = c.quote().leadTimeDays() != null ? c.quote().leadTimeDays() : 30;
                double leadScore = (minLead + 1.0) / (lead + 1.0);
                double qualityScore = c.quote().qualityScore() / 5.0;
                score = 100 * (w.price() * priceScore + w.leadTime() * leadScore + w.quality() * qualityScore);
                if (c.quote().equivalence() == Equivalence.EQUIVALENT) {
                    score *= EQUIVALENT_PENALTY;
                }
                score = Math.round(score * 10) / 10.0;
            }
            BigDecimal unitSaving = null;
            BigDecimal savingPct = null;
            if (referencePrice != null && referencePrice.signum() > 0) {
                unitSaving = referencePrice.subtract(c.quote().unitPrice()).setScale(3, RoundingMode.HALF_UP);
                savingPct = unitSaving.multiply(BigDecimal.valueOf(100))
                        .divide(referencePrice, 2, RoundingMode.HALF_UP);
            }
            ranked.add(new RankedOffer(c.quote(), 0, c.exclusion() == null, c.exclusion(), c.orderQty(), c.total(),
                    c.effective(), score, unitSaving, savingPct));
        }

        ranked.sort(Comparator.comparing(RankedOffer::eligible).reversed()
                .thenComparing(Comparator.comparingDouble(RankedOffer::score).reversed())
                .thenComparing(RankedOffer::effectiveUnitCost));
        List<RankedOffer> result = new ArrayList<>(ranked.size());
        int rank = 1;
        for (RankedOffer r : ranked) {
            result.add(new RankedOffer(r.quote(), r.eligible() ? rank++ : 0, r.eligible(), r.exclusionReason(),
                    r.orderQuantity(), r.totalCost(), r.effectiveUnitCost(), r.score(), r.unitSaving(),
                    r.savingPct()));
        }
        return result;
    }
}
