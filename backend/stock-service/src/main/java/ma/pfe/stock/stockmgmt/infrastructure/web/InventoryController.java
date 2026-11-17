package ma.pfe.stock.stockmgmt.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.stockmgmt.application.dto.InventoryView;
import ma.pfe.stock.stockmgmt.application.service.InventoryService;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API des inventaires physiques : /api/stock/inventories. */
@RestController
@Tag(name = "Inventaires", description = "Inventaires physiques : comptage, écarts, validation")
@RequestMapping("/api/stock/inventories")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Operation(summary = "Lister les inventaires")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<InventoryView> search(@RequestParam(required = false) InventoryStatus status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return inventoryService.search(status, page, size);
    }

    @Operation(summary = "Détail d'un inventaire avec ses lignes")
    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_READ)
    public InventoryView findById(@PathVariable Long id) {
        return inventoryService.findById(id);
    }

    @Operation(summary = "Ouvrir un inventaire")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MOVE_STOCK)
    public InventoryView open(@Valid @RequestBody StockRequests.InventoryRequest request) {
        return inventoryService.open(request.name(), request.notes(), CurrentUser.username());
    }

    /** Saisie (ou correction) du comptage d'un produit. */
    @Operation(summary = "Saisir ou corriger un comptage")
    @PutMapping("/{id}/counts")
    @PreAuthorize(Roles.CAN_MOVE_STOCK)
    public InventoryView count(@PathVariable Long id, @Valid @RequestBody StockRequests.CountRequest request) {
        return inventoryService.recordCount(id, request.productId(), request.countedQuantity(),
                CurrentUser.username());
    }

    @Operation(summary = "Retirer un comptage")
    @DeleteMapping("/{id}/counts/{productId}")
    @PreAuthorize(Roles.CAN_MOVE_STOCK)
    public InventoryView removeCount(@PathVariable Long id, @PathVariable Long productId) {
        return inventoryService.removeCount(id, productId);
    }

    /** Validation : génère les ajustements de stock (réservé ADMIN / GESTIONNAIRE). */
    @Operation(summary = "Valider l'inventaire", description = "Aligne le stock sur les quantités comptées (mouvements ADJUSTMENT).")
    @PostMapping("/{id}/validate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public InventoryView validate(@PathVariable Long id) {
        return inventoryService.validate(id, CurrentUser.username());
    }

    @Operation(summary = "Annuler l'inventaire")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public InventoryView cancel(@PathVariable Long id) {
        return inventoryService.cancel(id, CurrentUser.username());
    }
}
