package ma.pfe.stock.product.application.dto;

import ma.pfe.stock.product.domain.model.Category;

public record CategoryView(Long id, String name, String description) {

    public static CategoryView from(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getDescription());
    }
}
