package ma.pfe.stock.procurement.domain.model;

/** Origine d'un prix de l'historique. */
public enum PriceSource {
    /** Prix réellement payé (import de l'historique d'achats ou achat validé). */
    PURCHASE,
    /** Prix d'un devis reçu. */
    QUOTE,
    /** Prix publié sur le marché, relevé par la veille. */
    MARKET,
    /** Prix d'achat enregistré dans la fiche produit, relevé à chaque veille. */
    CATALOG
}
