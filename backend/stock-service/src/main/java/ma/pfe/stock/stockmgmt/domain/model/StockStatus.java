package ma.pfe.stock.stockmgmt.domain.model;

/** État d'un niveau de stock par rapport au seuil minimum. */
public enum StockStatus {
    OK,
    LOW,
    OUT_OF_STOCK;

    public static StockStatus of(int quantity, int minStock) {
        if (quantity <= 0) {
            return OUT_OF_STOCK;
        }
        if (quantity <= minStock) {
            return LOW;
        }
        return OK;
    }
}
