package ma.pfe.stock.product.domain.model;

/** Catégorie de produits. */
public class Category {

    private final Long id;
    private String name;
    private String description;

    public Category(Long id, String name, String description) {
        this.id = id;
        rename(name, description);
    }

    public static Category create(String name, String description) {
        return new Category(null, name, description);
    }

    public void rename(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la catégorie est obligatoire");
        }
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
