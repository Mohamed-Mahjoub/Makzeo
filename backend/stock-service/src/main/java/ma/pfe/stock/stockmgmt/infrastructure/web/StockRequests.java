package ma.pfe.stock.stockmgmt.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.stockmgmt.application.dto.MovementCommand;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;

public final class StockRequests {

    private StockRequests() {
    }

    /** Mouvement manuel : type IN (entrée) ou OUT (sortie). */
    public record MovementRequest(
            @NotNull(message = "Le produit est obligatoire") Long productId,
            @NotNull(message = "Le type est obligatoire (IN ou OUT)") MovementType type,
            @NotNull @Min(value = 1, message = "La quantité doit être >= 1") @Max(1_000_000) Integer quantity,
            Long supplierId,
            @Size(max = 100) String documentReference,
            @Size(max = 500) String reason) {

        public MovementCommand toCommand() {
            return new MovementCommand(productId, type, quantity, supplierId, documentReference, reason);
        }
    }

    public record InventoryRequest(
            @NotBlank(message = "Le libellé est obligatoire") @Size(max = 150) String name,
            @Size(max = 1000) String notes) {
    }

    public record CountRequest(
            @NotNull Long productId,
            @NotNull @Min(value = 0, message = "La quantité comptée doit être >= 0") @Max(1_000_000)
            Integer countedQuantity) {
    }
}
