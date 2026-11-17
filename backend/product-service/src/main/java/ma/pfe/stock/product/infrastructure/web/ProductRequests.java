package ma.pfe.stock.product.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.product.application.dto.ProductCommand;
import ma.pfe.stock.product.domain.model.UnitOfMeasure;

import java.math.BigDecimal;

public final class ProductRequests {

    private ProductRequests() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name,
            @Size(max = 500) String description) {
    }

    public record ProductRequest(
            @NotBlank(message = "La référence est obligatoire") @Size(max = 50)
            @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "Référence : lettres, chiffres et . _ / - uniquement")
            String reference,
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull(message = "La catégorie est obligatoire") Long categoryId,
            @Size(max = 32) String barcode,
            UnitOfMeasure unit,
            @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal purchasePrice,
            @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal salePrice,
            @NotNull @Min(0) Integer minStock,
            @Min(0) Integer maxStock,
            Long supplierId) {

        public ProductCommand toCommand() {
            return new ProductCommand(reference, name, description, categoryId, barcode, unit, purchasePrice,
                    salePrice, minStock, maxStock, supplierId);
        }
    }
}
