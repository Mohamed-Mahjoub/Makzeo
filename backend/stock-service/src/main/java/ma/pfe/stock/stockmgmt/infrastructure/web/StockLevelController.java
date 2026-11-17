package ma.pfe.stock.stockmgmt.infrastructure.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.stockmgmt.application.dto.DashboardView;
import ma.pfe.stock.stockmgmt.application.dto.StockLevelView;
import ma.pfe.stock.stockmgmt.application.service.StockQueryService;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API de consultation du stock : /api/stock/levels et /api/stock/dashboard. */
@RestController
@Tag(name = "Niveaux de stock", description = "Quantités disponibles et tableau de bord du stock")
@RequestMapping("/api/stock")
@Validated
public class StockLevelController {

    private final StockQueryService queryService;

    public StockLevelController(StockQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Lister les niveaux de stock", description = "Filtre status : OK, LOW, OUT_OF_STOCK.")
    @GetMapping("/levels")
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<StockLevelView> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) StockStatus status,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size) {
        return queryService.search(q, status, page, size);
    }

    @Operation(summary = "Niveaux de stock de plusieurs produits")
    @GetMapping("/levels/batch")
    @PreAuthorize(Roles.CAN_READ)
    public List<StockLevelView> batch(@RequestParam @Size(max = 1000) List<Long> productIds) {
        return queryService.levels(productIds);
    }

    @Operation(summary = "Niveau de stock d'un produit")
    @GetMapping("/levels/{productId}")
    @PreAuthorize(Roles.CAN_READ)
    public StockLevelView level(@PathVariable Long productId) {
        return queryService.level(productId);
    }

    @Operation(summary = "Indicateurs du stock", description = "Produits suivis, ruptures, alertes actives, mouvements du jour.")
    @GetMapping("/dashboard")
    @PreAuthorize(Roles.CAN_READ)
    public DashboardView dashboard() {
        return queryService.dashboard();
    }
}
