package ma.pfe.stock.stockmgmt.domain.model;

/** Copie des informations produit utiles au stock (issues de product-service). */
public record ProductSnapshot(Long id, String reference, String name, int minStock, boolean active) {
}
