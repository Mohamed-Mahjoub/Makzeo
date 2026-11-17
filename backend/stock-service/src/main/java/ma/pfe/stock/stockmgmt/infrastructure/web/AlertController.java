package ma.pfe.stock.stockmgmt.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.stockmgmt.application.dto.AlertView;
import ma.pfe.stock.stockmgmt.application.service.AlertService;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** API des alertes de stock : /api/stock/alerts. */
@RestController
@Tag(name = "Alertes", description = "Alertes de rupture et de seuil minimum")
@RequestMapping("/api/stock/alerts")
@Validated
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "Lister les alertes")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<AlertView> search(@RequestParam(required = false) AlertStatus status,
                                        @RequestParam(required = false) AlertType type,
                                        @RequestParam(required = false) Boolean activeOnly,
                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                        @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size) {
        return alertService.search(status, type, activeOnly, page, size);
    }

    @Operation(summary = "Prendre en compte une alerte")
    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize(Roles.CAN_MOVE_STOCK)
    public AlertView acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id, CurrentUser.username());
    }

    /** Réévalue toutes les alertes (après modification des seuils minimum dans le catalogue). */
    @Operation(summary = "Recalculer toutes les alertes", description = "À lancer après une modification des seuils minimum.")
    @PostMapping("/recompute")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public Map<String, Integer> recompute() {
        return Map.of("evaluatedProducts", alertService.recomputeAll());
    }
}
