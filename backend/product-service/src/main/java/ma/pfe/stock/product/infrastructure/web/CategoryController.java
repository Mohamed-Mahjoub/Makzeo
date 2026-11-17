package ma.pfe.stock.product.infrastructure.web;

import jakarta.validation.Valid;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.product.application.dto.CategoryView;
import ma.pfe.stock.product.application.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API des catégories : /api/categories. */
@RestController
@Tag(name = "Catégories", description = "Catégories de produits")
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Lister les catégories")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public List<CategoryView> findAll() {
        return categoryService.findAll();
    }

    @Operation(summary = "Détail d'une catégorie")
    @GetMapping("/{id}")
    @PreAuthorize(Roles.CAN_READ)
    public CategoryView findById(@PathVariable Long id) {
        return categoryService.findById(id);
    }

    @Operation(summary = "Créer une catégorie")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public CategoryView create(@Valid @RequestBody ProductRequests.CategoryRequest request) {
        return categoryService.create(request.name(), request.description());
    }

    @Operation(summary = "Modifier une catégorie")
    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public CategoryView update(@PathVariable Long id, @Valid @RequestBody ProductRequests.CategoryRequest request) {
        return categoryService.update(id, request.name(), request.description());
    }

    @Operation(summary = "Supprimer une catégorie", description = "Refusé (409) si des produits y sont rattachés.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(Roles.CAN_MANAGE_CATALOG)
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }
}
