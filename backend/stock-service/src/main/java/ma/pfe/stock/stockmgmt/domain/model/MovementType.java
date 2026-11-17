package ma.pfe.stock.stockmgmt.domain.model;

/** Type de mouvement de stock. */
public enum MovementType {
    /** Entrée (réception fournisseur, retour...). */
    IN("Entrée"),
    /** Sortie (consommation, vente, perte...). */
    OUT("Sortie"),
    /** Ajustement issu d'un inventaire (positif ou négatif). */
    ADJUSTMENT("Ajustement inventaire");

    private final String label;

    MovementType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
