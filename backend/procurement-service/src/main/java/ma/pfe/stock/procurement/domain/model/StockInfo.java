package ma.pfe.stock.procurement.domain.model;

/**
 * Situation de stock d'un article.
 *
 * @param monthlyConsumption sorties moyennes par mois sur la période observée
 */
public record StockInfo(Long productId, int quantity, double monthlyConsumption) {

    public static StockInfo unknown(Long productId) {
        return new StockInfo(productId, 0, 0);
    }
}
