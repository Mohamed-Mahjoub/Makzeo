package ma.pfe.stock.procurement.domain.model;

/** Disponibilité annoncée par le fournisseur. */
public enum Availability {
    IN_STOCK("En stock"),
    ON_ORDER("Sur commande"),
    UNAVAILABLE("Indisponible");

    private final String label;

    Availability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
