package ma.pfe.stock.reporting.domain.model;

import java.time.Instant;
import java.util.List;

public record InventoryData(Long id, String name, String status, String createdBy, Instant createdAt,
                            String closedBy, Instant closedAt, List<Line> lines) {

    public record Line(String productReference, String productName, int expectedQuantity, int countedQuantity,
                       int difference, String countedBy) {
    }
}
