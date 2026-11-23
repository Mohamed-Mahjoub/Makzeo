package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.OverviewView;
import ma.pfe.stock.procurement.application.dto.WatchRunView;
import ma.pfe.stock.procurement.application.service.MarketWatchService;
import ma.pfe.stock.procurement.application.service.OverviewService;
import ma.pfe.stock.procurement.domain.model.SourcingLead;
import ma.pfe.stock.procurement.domain.model.WatchRun;
import ma.pfe.stock.procurement.domain.service.SavingsCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Synthèse, économies et veille du marché : /api/procurement. */
@RestController
@Tag(name = "Achats - veille", description = "Synthèse, économies, veille du marché")
@RequestMapping("/api/procurement")
@Validated
public class ProcurementController {

    private final OverviewService overview;
    private final MarketWatchService watch;

    public ProcurementController(OverviewService overview, MarketWatchService watch) {
        this.overview = overview;
        this.watch = watch;
    }

    @Operation(summary = "Synthèse du module achats")
    @GetMapping("/overview")
    @PreAuthorize(Roles.CAN_REPORT)
    public OverviewView overview() {
        return overview.overview();
    }

    @Operation(summary = "Économies réalisées et potentielles", description = "Par article, fournisseur et mois.")
    @GetMapping("/savings")
    @PreAuthorize(Roles.CAN_REPORT)
    public SavingsCalculator.Summary savings() {
        return overview.savings();
    }

    @Operation(summary = "Lancer la veille du marché", description = "Exécution en arrière-plan ; une seule à la fois.")
    @PostMapping("/watch/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public WatchRunView runWatch() {
        return watch.start(WatchRun.Trigger.MANUAL, CurrentUser.username());
    }

    @Operation(summary = "Historique des veilles")
    @GetMapping("/watch/runs")
    @PreAuthorize(Roles.CAN_REPORT)
    public PageResult<WatchRunView> runs(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return watch.history(page, size);
    }

    @Operation(summary = "Opportunités détectées par la veille")
    @GetMapping("/leads")
    @PreAuthorize(Roles.CAN_REPORT)
    public List<SourcingLead> leads(@RequestParam(required = false) Long productId) {
        return watch.leads(productId);
    }
}
