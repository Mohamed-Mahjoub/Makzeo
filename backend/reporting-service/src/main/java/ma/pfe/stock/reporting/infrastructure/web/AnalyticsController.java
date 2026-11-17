package ma.pfe.stock.reporting.infrastructure.web;

import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.reporting.application.dto.AnalyticsView;
import ma.pfe.stock.reporting.application.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicateurs du tableau de bord décisionnel : /api/reports/analytics (tous les rôles). */
@RestController
@Tag(name = "Tableau de bord", description = "Indicateurs décisionnels : état des stocks, réapprovisionnement, valorisation")
@RequestMapping("/api/reports/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * @param days       période d'analyse en jours (7 à 365, défaut 30)
     * @param categoryId filtre optionnel sur une catégorie
     */
    @Operation(summary = "Indicateurs du tableau de bord", description = "KPI, flux quotidiens, valorisation par catégorie, prévision de réapprovisionnement (période 7 à 365 jours).")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public AnalyticsView analytics(@RequestParam(defaultValue = "30") int days,
                                   @RequestParam(required = false) Long categoryId) {
        return analyticsService.compute(days, categoryId);
    }
}
