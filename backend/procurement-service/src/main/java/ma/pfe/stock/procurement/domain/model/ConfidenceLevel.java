package ma.pfe.stock.procurement.domain.model;

/** Niveau de confiance d'une prédiction. */
public enum ConfidenceLevel {
    HIGH("Élevée"),
    MEDIUM("Moyenne"),
    LOW("Faible");

    private final String label;

    ConfidenceLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ConfidenceLevel of(double confidence) {
        if (confidence >= 0.65) {
            return HIGH;
        }
        return confidence >= 0.4 ? MEDIUM : LOW;
    }
}
