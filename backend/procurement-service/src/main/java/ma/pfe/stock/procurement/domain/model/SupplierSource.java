package ma.pfe.stock.procurement.domain.model;

/** Origine d'un fournisseur du marché. */
public enum SupplierSource {
    /** Fournisseur déjà référencé dans le catalogue (supplier-service). */
    CATALOG,
    /** Détecté par la veille automatique. */
    WEB_WATCH,
    /** Ajouté manuellement par un acheteur. */
    MANUAL
}
