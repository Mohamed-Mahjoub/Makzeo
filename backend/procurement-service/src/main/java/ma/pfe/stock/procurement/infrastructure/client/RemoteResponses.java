package ma.pfe.stock.procurement.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/** Sous-ensembles des représentations JSON des autres services. */
final class RemoteResponses {

    private RemoteResponses() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(Long id, String reference, String name, String description, String categoryName, String barcode,
                   String unit, BigDecimal purchasePrice, Integer minStock, Long supplierId, Boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Supplier(Long id, String code, String name, String email, String phone, String city, String country,
                    Boolean active) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StockLevel(Long productId, Integer quantity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Movement(Long productId, String type, Integer quantity) {
    }
}
