package ma.pfe.stock.procurement.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.procurement.application.dto.ComparisonView;
import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.application.service.QuoteService;
import ma.pfe.stock.procurement.domain.model.Quote;
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

import java.util.List;

/** Devis et comparaison des offres : /api/procurement/quotes. */
@RestController
@Tag(name = "Achats - devis", description = "Collecte des devis et comparaison qualité-prix")
@RequestMapping("/api/procurement/quotes")
@Validated
public class QuoteController {

    private final QuoteService service;

    public QuoteController(QuoteService service) {
        this.service = service;
    }

    @Operation(summary = "Devis d'un article")
    @GetMapping
    @PreAuthorize(Roles.CAN_REPORT)
    public List<Quote> byProduct(@RequestParam Long productId) {
        return service.findByProduct(productId);
    }

    @Operation(summary = "Enregistrer un devis", description = "Un prix TTC est ramené en HT.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public Quote register(@Valid @RequestBody ProcurementRequests.QuoteRequest request) {
        return service.register(request.toCommand());
    }

    @Operation(summary = "Lire un devis avec l'IA", description = "Proposition à vérifier avant enregistrement ; rien n'est sauvegardé.")
    @PostMapping("/extract")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public MarketIntelligence.ExtractedQuote extract(@Valid @RequestBody ProcurementRequests.ExtractRequest request) {
        return service.extract(request.productId(), request.text());
    }

    @Operation(summary = "Comparer les offres d'un article", description = "Classement par rapport qualité-prix pour la quantité indiquée.")
    @GetMapping("/comparison")
    @PreAuthorize(Roles.CAN_REPORT)
    public ComparisonView compare(@RequestParam Long productId,
                                  @RequestParam(required = false) @Min(1) @Max(1_000_000) Integer quantity) {
        return service.compare(productId, quantity);
    }
}
