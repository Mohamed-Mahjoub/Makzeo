package ma.pfe.stock.product.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.product.application.dto.ProductView;
import ma.pfe.stock.product.application.service.ProductService;
import ma.pfe.stock.product.domain.port.ProductRepository;
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

/**
 * API des produits : /api/products.
 * Pas de suppression physique : un produit ayant un historique de stock est désactivé.
 */
@RestController
@Tag(name = "Produits", description = "Catalogue : références, prix, seuils et codes-barres")
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Rechercher des produits", description = "Recherche multicritère paginée (nom, référence, code-barres, catégorie, fournisseur, état).")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public PageResult<ProductView> search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) Long supplierId,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return productService.search(new ProductRepository.ProductCriteria(q, categoryId, supplierId, active),
                page, size);
    }

    /** Récupération groupée (utilisée par stock-service et reporting-service). */
    @Operation(summary = "Lecture groupée de produits", description = "Utilisée par les autres microservices.")
    @GetMapping("/batch")
    @PreAuthorize(Roles.CAN_READ)
    public List<ProductView> findByIds(@RequestParam @Size(max = 1000) List<Long> ids) {
        return productService.findByIds(ids);
    }

    @Operation(summary = "Détail d'un produit")
    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_READ)
    public ProductView findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @Operation(summary = "Rechercher par référence")
    @GetMapping("/reference/{reference}")
    @PreAuthorize(Roles.CAN_READ)
    public ProductView findByReference(@PathVariable String reference) {
        return productService.findByReference(reference);
    }

    /** Recherche par scan de code-barres. */
    @Operation(summary = "Rechercher par code-barres (scan)")
    @GetMapping("/barcode/{barcode}")
    @PreAuthorize(Roles.CAN_READ)
    public ProductView findByBarcode(@PathVariable String barcode) {
        return productService.findByBarcode(barcode);
    }

    @Operation(summary = "Créer un produit", description = "Si le code-barres est absent, un EAN-13 interne est généré.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public ProductView create(@Valid @RequestBody ProductRequests.ProductRequest request) {
        return productService.create(request.toCommand());
    }

    @Operation(summary = "Modifier un produit")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductRequests.ProductRequest request) {
        return productService.update(id, request.toCommand());
    }

    @Operation(summary = "Réactiver un produit")
    @PatchMapping("/{id}/activate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public ProductView activate(@PathVariable Long id) {
        return productService.setActive(id, true);
    }

    @Operation(summary = "Désactiver un produit", description = "Pas de suppression physique : l'historique est conservé.")
    @PatchMapping("/{id}/deactivate")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public ProductView deactivate(@PathVariable Long id) {
        return productService.setActive(id, false);
    }
}
