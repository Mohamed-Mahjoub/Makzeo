package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.RfqView;
import ma.pfe.stock.procurement.application.service.RfqService;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Demandes de prix : /api/procurement/rfqs. */
@RestController
@Tag(name = "Achats - demandes de prix", description = "Brouillons préparés par l'agent, validation, envoi")
@RequestMapping("/api/procurement/rfqs")
@Validated
public class RfqController {

    private final RfqService service;

    public RfqController(RfqService service) {
        this.service = service;
    }

    @Operation(summary = "Lister les demandes de prix")
    @GetMapping
    @PreAuthorize(Roles.CAN_REPORT)
    public PageResult<RfqView> search(@RequestParam(required = false) RfqStatus status,
                                      @RequestParam(required = false) Long productId,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return service.search(status, productId, page, size);
    }

    @Operation(summary = "Détail d'une demande de prix")
    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_REPORT)
    public RfqView findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @Operation(summary = "Préparer une demande de prix (brouillon)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RfqView draft(@Valid @RequestBody ProcurementRequests.RfqDraftRequest request) {
        return service.draft(request.productId(), request.marketSupplierId(), request.quantity(),
                CurrentUser.username());
    }

    @Operation(summary = "Modifier un brouillon")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RfqView update(@PathVariable Long id, @Valid @RequestBody ProcurementRequests.RfqUpdateRequest request) {
        return service.update(id, request.quantity(), request.subject(), request.body(), request.responseDueDate());
    }

    @Operation(summary = "Valider et envoyer", description = "Envoi par courriel au fournisseur (statut SENT ou FAILED).")
    @PostMapping("/{id}/approve")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RfqView approve(@PathVariable Long id) {
        return service.approveAndSend(id, CurrentUser.username());
    }

    @Operation(summary = "Valider et envoyer plusieurs demandes")
    @PostMapping("/approve")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public List<RfqView> approveAll(@Valid @RequestBody ProcurementRequests.IdsRequest request) {
        return service.approveAndSendAll(request.ids(), CurrentUser.username());
    }

    @Operation(summary = "Annuler une demande de prix non envoyée")
    @PostMapping("/{id}/cancel")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public RfqView cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
