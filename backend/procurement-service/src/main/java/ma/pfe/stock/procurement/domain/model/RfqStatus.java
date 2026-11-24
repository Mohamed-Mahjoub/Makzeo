package ma.pfe.stock.procurement.domain.model;

/** Cycle de vie d'une demande de prix. */
public enum RfqStatus {
    /** Préparée par l'agent ou un acheteur, en attente de validation. */
    DRAFT,
    /** Validée par le responsable des achats, en cours d'envoi. */
    APPROVED,
    SENT,
    /** Un devis a été enregistré en réponse. */
    ANSWERED,
    /** L'envoi a échoué (SMTP) : peut être validée à nouveau. */
    FAILED,
    CANCELLED
}
