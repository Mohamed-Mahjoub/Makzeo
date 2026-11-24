package ma.pfe.stock.procurement.domain.model;

/** Correspondance entre l'article proposé par un fournisseur et l'article du catalogue. */
public enum Equivalence {
    IDENTICAL("Article identique"),
    EQUIVALENT("Article équivalent");

    private final String label;

    Equivalence(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
