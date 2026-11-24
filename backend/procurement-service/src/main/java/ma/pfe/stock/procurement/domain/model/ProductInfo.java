package ma.pfe.stock.procurement.domain.model;

import java.math.BigDecimal;

/** Instantané d'un article du catalogue (product-service). */
public record ProductInfo(Long id, String reference, String name, String description, String barcode,
                          String categoryName, String unit, BigDecimal purchasePrice, int minStock, Long supplierId,
                          boolean active) {
}
