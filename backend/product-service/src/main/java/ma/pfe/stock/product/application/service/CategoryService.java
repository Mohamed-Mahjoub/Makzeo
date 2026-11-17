package ma.pfe.stock.product.application.service;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.product.application.dto.CategoryView;
import ma.pfe.stock.product.domain.model.Category;
import ma.pfe.stock.product.domain.port.CategoryRepository;
import ma.pfe.stock.product.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Cas d'utilisation : gestion des catégories. */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> findAll() {
        return categoryRepository.findAll().stream().map(CategoryView::from).toList();
    }

    @Transactional(readOnly = true)
    public CategoryView findById(Long id) {
        return CategoryView.from(get(id));
    }

    @Transactional
    public CategoryView create(String name, String description) {
        if (categoryRepository.existsByNameIgnoreCase(name.trim())) {
            throw new DuplicateResourceException("La catégorie '" + name + "' existe déjà");
        }
        return CategoryView.from(categoryRepository.save(Category.create(name, description)));
    }

    @Transactional
    public CategoryView update(Long id, String name, String description) {
        Category category = get(id);
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name.trim(), id)) {
            throw new DuplicateResourceException("La catégorie '" + name + "' existe déjà");
        }
        category.rename(name, description);
        return CategoryView.from(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        long products = productRepository.countByCategoryId(id);
        if (products > 0) {
            throw new BusinessRuleException("CATEGORY_NOT_EMPTY",
                    "Impossible de supprimer la catégorie : " + products + " produit(s) y sont rattachés");
        }
        categoryRepository.deleteById(id);
    }

    Category get(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Catégorie", id));
    }
}
