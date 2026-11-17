package ma.pfe.stock.stockmgmt.application.dto;

import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;

import java.time.Instant;

public record StockLevelView(Long productId, String productReference, String productName, int quantity,
                             int minStock, StockStatus status, Instant lastMovementAt) {

    public static StockLevelView from(StockItem item) {
        return new StockLevelView(item.getProductId(), item.getProductReference(), item.getProductName(),
                item.getQuantity(), item.getMinStock(), item.status(), item.getLastMovementAt());
    }
}
