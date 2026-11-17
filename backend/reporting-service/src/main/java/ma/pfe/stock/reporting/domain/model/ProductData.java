package ma.pfe.stock.reporting.domain.model;

import java.math.BigDecimal;

/** Données produit utilisées par les rapports. */
public record ProductData(Long id, String reference, String name, String categoryName, String barcode, String unit,
                          BigDecimal purchasePrice, BigDecimal salePrice, int minStock, Integer maxStock,
                          boolean active) {
}
