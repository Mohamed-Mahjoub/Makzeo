package ma.pfe.stock.procurement.domain.model;

public enum RecommendationType {
    /** Acheter maintenant, avant la hausse de prix prévue. */
    ANTICIPATED_PURCHASE,
    /** Changer de fournisseur : une offre est moins chère que le dernier prix payé. */
    SWITCH_SUPPLIER
}
