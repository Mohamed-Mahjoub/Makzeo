package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.RecommendationView;
import ma.pfe.stock.procurement.application.service.RecommendationService;
import ma.pfe.stock.procurement.domain.model.RecommendationStatus;
import ma.pfe.stock.procurement.domain.model.RecommendationType;
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

/** Recommandations d'achat : /api/procurement/recommendations. */
@RestController
@Tag(name = "Achats - recommandations", description = "Achats anticipés et changements de fournisseur")
@RequestMapping("/api/procurement/recommendations")
@Validated
public class RecommendationController {

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @Operation(summary = "Lister les recommandations", description = "Triées par économie décroissante.")
    @GetMapping
    @PreAuthorize(Roles.CAN_REPORT)
    public PageResult<RecommendationView> search(@RequestParam(required = false) RecommendationStatus status,
                                                 @RequestParam(required = false) RecommendationType type,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return service.search(status, type, page, size);
    }

    @Operation(summary = "Recalculer les recommandations", description = "À partir des devis et prédictions existants, sans appel à l'IA.")
    @PostMapping("/refresh")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public Map<String, Integer> refresh() {
        int created = service.refreshAll();
        int notified = service.notifyOpportunities();
        return Map.of("created", created, "notified", notified);
    }

    @Operation(summary = "Accepter une recommandation")
    @PatchMapping("/{id}/accept")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RecommendationView accept(@PathVariable Long id) {
        return service.accept(id, CurrentUser.username());
    }

    @Operation(summary = "Écarter une recommandation")
    @PatchMapping("/{id}/dismiss")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RecommendationView dismiss(@PathVariable Long id) {
        return service.dismiss(id, CurrentUser.username());
    }
}
