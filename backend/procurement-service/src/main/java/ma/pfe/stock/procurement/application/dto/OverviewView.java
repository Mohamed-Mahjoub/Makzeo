package ma.pfe.stock.procurement.application.dto;

import java.math.BigDecimal;

/** Synthèse du module pour le tableau de bord des achats. */
public record OverviewView(boolean aiEnabled, String aiProvider, boolean mailEnabled, String currency, String marketName,
                           WatchRunView lastRun, long candidateSuppliers, long validatedSuppliers, long draftRfqs,
                           long sentRfqs, long openOpportunities, BigDecimal potentialSavings,
                           BigDecimal realizedSavings, long risingPrices, long fallingPrices) {
}
