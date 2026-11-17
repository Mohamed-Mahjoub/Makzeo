package ma.pfe.stock.product.application.dto;

import ma.pfe.stock.product.domain.model.UnitOfMeasure;

import java.math.BigDecimal;

/**
 * Données de création / modification d'un produit.
 * Si {@code barcode} est vide, un EAN-13 interne est généré automatiquement.
 */
public record ProductCommand(String reference, String name, String description, Long categoryId, String barcode,
                             UnitOfMeasure unit, BigDecimal purchasePrice, BigDecimal salePrice, int minStock,
                             Integer maxStock, Long supplierId) {
}
