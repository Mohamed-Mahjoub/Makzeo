package ma.pfe.stock.product.infrastructure.persistence;

import ma.pfe.stock.product.domain.model.Category;
import ma.pfe.stock.product.domain.port.CategoryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = category.getId() == null
                ? new CategoryEntity()
                : jpa.findById(category.getId()).orElseGet(CategoryEntity::new);
        entity.setName(category.getName());
        entity.setDescription(category.getDescription());
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAll(Sort.by("name")).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return jpa.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByNameIgnoreCaseAndIdNot(String name, Long id) {
        return jpa.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    private Category toDomain(CategoryEntity e) {
        return new Category(e.getId(), e.getName(), e.getDescription());
    }
}
