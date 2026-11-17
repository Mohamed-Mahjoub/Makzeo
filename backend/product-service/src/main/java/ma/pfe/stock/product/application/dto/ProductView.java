package ma.pfe.stock.product.application.dto;

import ma.pfe.stock.product.domain.model.Product;
import ma.pfe.stock.product.domain.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.time.Instant;

/** Vue d'un produit exposée par l'API. */
public record ProductView(Long id, String reference, String name, String description, Long categoryId,
                          String categoryName, String barcode, UnitOfMeasure unit, BigDecimal purchasePrice,
                          BigDecimal salePrice, int minStock, Integer maxStock, Long supplierId, boolean active,
                          Instant createdAt, Instant updatedAt) {

    public static ProductView from(Product p, String categoryName) {
        return new ProductView(p.getId(), p.getReference(), p.getName(), p.getDescription(), p.getCategoryId(),
                categoryName, p.getBarcode().value(), p.getUnit(), p.getPurchasePrice(), p.getSalePrice(),
                p.getMinStock(), p.getMaxStock(), p.getSupplierId(), p.isActive(), p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
