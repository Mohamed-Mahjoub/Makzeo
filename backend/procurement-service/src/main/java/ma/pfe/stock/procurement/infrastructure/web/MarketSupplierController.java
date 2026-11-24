package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.MarketSupplierView;
import ma.pfe.stock.procurement.application.service.MarketSupplierService;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Fournisseurs du marché (catalogue, détectés, saisis) : /api/procurement/suppliers. */
@RestController
@Tag(name = "Achats - fournisseurs du marché", description = "Fournisseurs détectés par la veille, à valider")
@RequestMapping("/api/procurement/suppliers")
@Validated
public class MarketSupplierController {

    private final MarketSupplierService service;

    public MarketSupplierController(MarketSupplierService service) {
        this.service = service;
    }

    @Operation(summary = "Lister les fournisseurs du marché")
    @GetMapping
    @PreAuthorize(Roles.CAN_REPORT)
    public PageResult<MarketSupplierView> search(@RequestParam(required = false) MarketSupplierStatus status,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return service.search(status, page, size);
    }

    @Operation(summary = "Ajouter un fournisseur")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public MarketSupplierView create(@Valid @RequestBody ProcurementRequests.SupplierRequest request) {
        return service.create(request.toCommand());
    }

    @Operation(summary = "Modifier un fournisseur (coordonnées)")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public MarketSupplierView update(@PathVariable Long id,
                                     @Valid @RequestBody ProcurementRequests.SupplierRequest request) {
        return service.update(id, request.toCommand());
    }

    @Operation(summary = "Valider un fournisseur détecté", description = "Indispensable avant toute demande de prix.")
    @PatchMapping("/{id}/validate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public MarketSupplierView validate(@PathVariable Long id) {
        return service.validate(id);
    }

    @Operation(summary = "Écarter un fournisseur détecté")
    @PatchMapping("/{id}/reject")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public MarketSupplierView reject(@PathVariable Long id) {
        return service.reject(id);
    }
}
