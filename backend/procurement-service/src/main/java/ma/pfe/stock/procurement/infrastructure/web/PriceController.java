package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.ImportResult;
import ma.pfe.stock.procurement.application.dto.PriceHistoryView;
import ma.pfe.stock.procurement.application.service.PriceService;
import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Historique des prix et prédictions : /api/procurement/prices. */
@RestController
@Tag(name = "Achats - prix", description = "Historique des prix d'achat et prédictions")
@RequestMapping("/api/procurement/prices")
@Validated
public class PriceController {

    private final PriceService service;

    public PriceController(PriceService service) {
        this.service = service;
    }

    @Operation(summary = "Prédictions de prix de tous les articles", description = "Triées de la plus forte hausse à la plus forte baisse.")
    @GetMapping("/predictions")
    @PreAuthorize(Roles.CAN_REPORT)
    public List<PricePrediction> predictions() {
        return service.predictions();
    }

    @Operation(summary = "Historique et prédiction d'un article")
    @GetMapping("/{productId}")
    @PreAuthorize(Roles.CAN_REPORT)
    public PriceHistoryView history(@PathVariable Long productId) {
        return service.history(productId);
    }

    @Operation(summary = "Enregistrer un prix d'achat payé")
    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public PricePoint recordPurchase(@Valid @RequestBody ProcurementRequests.PurchaseRequest request) {
        return service.record(request.productId(), request.unitPrice(), request.date(), PriceSource.PURCHASE,
                request.supplierName(), request.quantity());
    }

    @Operation(summary = "Importer l'historique d'achats (CSV)",
            description = "Une ligne par achat : date;référence;fournisseur;quantité;prix unitaire HT.")
    @PostMapping("/purchases/import")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public ImportResult importPurchases(@Valid @RequestBody ProcurementRequests.ImportRequest request) {
        return service.importPurchases(request.csv());
    }
}
