package ma.pfe.stock.stockmgmt.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

/** Levée lorsqu'une sortie dépasse la quantité disponible. */
public class InsufficientStockException extends BusinessRuleException {

    public InsufficientStockException(String productReference, int available, int requested) {
        super("INSUFFICIENT_STOCK", "Stock insuffisant pour " + productReference + " : disponible " + available
                + ", demandé " + requested);
    }
}
