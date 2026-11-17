package ma.pfe.stock.supplier.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.supplier.application.dto.SupplierView;
import ma.pfe.stock.supplier.application.service.SupplierService;
import ma.pfe.stock.supplier.domain.model.Countries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

/** API des fournisseurs : /api/suppliers. */
@RestController
@Tag(name = "Fournisseurs", description = "Gestion des fournisseurs")
@RequestMapping("/api/suppliers")
@Validated
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    @Operation(summary = "Rechercher des fournisseurs")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<SupplierView> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(required = false) String country,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return service.search(q, active, country, page, size);
    }

    @Operation(summary = "Liste des pays", description = "Tous les pays (ISO 3166-1 alpha-2) avec leur nom en français.")
    @GetMapping("/countries")
    @PreAuthorize(Roles.CAN_READ)
    public List<Countries.Country> countries() {
        return service.countries();
    }

    @Operation(summary = "Lecture groupée de fournisseurs")
    @GetMapping("/batch")
    @PreAuthorize(Roles.CAN_READ)
    public List<SupplierView> findByIds(@RequestParam @Size(max = 1000) List<Long> ids) {
        return service.findByIds(ids);
    }

    @Operation(summary = "Détail d'un fournisseur")
    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_READ)
    public SupplierView findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(summary = "Créer un fournisseur")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public SupplierView create(@Valid @RequestBody SupplierRequest request) {
        return service.create(request.toCommand());
    }

    @Operation(summary = "Modifier un fournisseur")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public SupplierView update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return service.update(id, request.toCommand());
    }

    @Operation(summary = "Activer un fournisseur")
    @PatchMapping("/{id}/activate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public SupplierView activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @Operation(summary = "Désactiver un fournisseur")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public SupplierView deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }
}
