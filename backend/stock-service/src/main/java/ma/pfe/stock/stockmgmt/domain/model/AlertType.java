package ma.pfe.stock.stockmgmt.domain.model;

public enum AlertType {
    /** Stock à zéro. */
    RUPTURE("Rupture de stock"),
    /** Stock inférieur ou égal au seuil minimum. */
    SEUIL_MIN("Seuil minimum atteint");

    private final String label;

    AlertType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
