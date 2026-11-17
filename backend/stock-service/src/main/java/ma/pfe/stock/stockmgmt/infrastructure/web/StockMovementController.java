package ma.pfe.stock.stockmgmt.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.stockmgmt.application.dto.MovementView;
import ma.pfe.stock.stockmgmt.application.service.StockMovementService;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.port.StockMovementRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/** API des mouvements de stock (entrées / sorties / historique) : /api/stock/movements. */
@RestController
@Tag(name = "Mouvements de stock", description = "Entrées, sorties et historique des mouvements")
@RequestMapping("/api/stock/movements")
@Validated
public class StockMovementController {

    private final StockMovementService movementService;
    private final ZoneId zoneId;

    public StockMovementController(StockMovementService movementService, ZoneId zoneId) {
        this.movementService = movementService;
        this.zoneId = zoneId;
    }

    @Operation(summary = "Enregistrer une entrée (IN) ou une sortie (OUT)", description = "Met à jour le stock et déclenche ou résout les alertes. Sortie refusée (409) si le stock est insuffisant.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MOVE_STOCK)
    public MovementView record(@Valid @RequestBody StockRequests.MovementRequest request) {
        return movementService.record(request.toCommand(), CurrentUser.username());
    }

    /** Historique des mouvements, filtrable (dates au format AAAA-MM-JJ, bornes incluses). */
    @Operation(summary = "Historique des mouvements", description = "Filtres : produit, type, période (AAAA-MM-JJ), utilisateur, fournisseur, inventaire.")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<MovementView> history(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long inventoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(1000) int size) {
        StockMovementRepository.MovementCriteria criteria = new StockMovementRepository.MovementCriteria(
                productId, type,
                from == null ? null : from.atStartOfDay(zoneId).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toInstant(),
                user, supplierId, inventoryId);
        return movementService.search(criteria, page, size);
    }
}
