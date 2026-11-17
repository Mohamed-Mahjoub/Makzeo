package ma.pfe.stock.stockmgmt.application.dto;

import ma.pfe.stock.stockmgmt.domain.model.Inventory;
import ma.pfe.stock.stockmgmt.domain.model.InventoryLine;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;

import java.time.Instant;
import java.util.List;

public record InventoryView(Long id, String name, String notes, InventoryStatus status, String createdBy,
                            Instant createdAt, String closedBy, Instant closedAt, int lineCount,
                            int discrepancyCount, List<LineView> lines) {

    public record LineView(Long productId, String productReference, String productName, int expectedQuantity,
                           int countedQuantity, int difference, String countedBy, Instant countedAt) {

        static LineView from(InventoryLine l) {
            return new LineView(l.productId(), l.productReference(), l.productName(), l.expectedQuantity(),
                    l.countedQuantity(), l.difference(), l.countedBy(), l.countedAt());
        }
    }

    public static InventoryView from(Inventory inv, boolean withLines) {
        List<InventoryLine> lines = inv.getLines();
        int discrepancies = (int) lines.stream().filter(l -> l.difference() != 0).count();
        return new InventoryView(inv.getId(), inv.getName(), inv.getNotes(), inv.getStatus(), inv.getCreatedBy(),
                inv.getCreatedAt(), inv.getClosedBy(), inv.getClosedAt(), lines.size(), discrepancies,
                withLines ? lines.stream().map(LineView::from).toList() : List.of());
    }
}
