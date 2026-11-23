package ma.pfe.stock.procurement.domain.model;

/** Origine d'une offre de prix. */
public enum QuoteSource {
    /** Réponse d'un fournisseur à une demande de prix. */
    RFQ_REPLY,
    /** Saisie par un acheteur. */
    MANUAL,
    /** Prix publié sur le site du fournisseur, relevé par la veille. */
    WEB,
    /** Devis collé dans l'application et lu par l'IA, validé par l'acheteur. */
    AI_EXTRACTED
}
