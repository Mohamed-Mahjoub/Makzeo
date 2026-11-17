package ma.pfe.stock.reporting.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Représentations JSON (partielles) des réponses des autres microservices. */
final class RemoteResponses {

    private RemoteResponses() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(Long id, String reference, String name, String categoryName, String barcode, String unit,
                   BigDecimal purchasePrice, BigDecimal salePrice, Integer minStock, Integer maxStock,
                   Boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockLevel(Long productId, Integer quantity, String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Movement(Long id, Long productId, String productReference, String productName, String type, String typeLabel,
                    int quantity, int signedQuantity, int quantityBefore, int quantityAfter,
                    String documentReference, String reason, String performedBy, Instant occurredAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Alert(Long id, String productReference, String productName, String type, String typeLabel,
                 String status, int quantity, int threshold, Instant createdAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Inventory(Long id, String name, String status, String createdBy, Instant createdAt, String closedBy,
                     Instant closedAt, List<InventoryLine> lines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InventoryLine(String productReference, String productName, int expectedQuantity, int countedQuantity,
                         int difference, String countedBy) {
    }
}
