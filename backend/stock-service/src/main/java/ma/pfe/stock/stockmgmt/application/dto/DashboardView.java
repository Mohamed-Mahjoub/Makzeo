package ma.pfe.stock.stockmgmt.application.dto;

/** Indicateurs clés pour le tableau de bord du frontend. */
public record DashboardView(long trackedProducts, long totalQuantity, long outOfStockProducts,
                            long lowStockProducts, long activeRuptureAlerts, long activeThresholdAlerts,
                            long entriesToday, long exitsToday, long adjustmentsToday) {
}
