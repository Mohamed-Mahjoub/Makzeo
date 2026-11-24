package ma.pfe.stock.procurement.domain.model;

/** Cycle de vie d'un fournisseur détecté : il doit être validé avant toute demande de prix. */
public enum MarketSupplierStatus {
    CANDIDATE, VALIDATED, REJECTED
}
