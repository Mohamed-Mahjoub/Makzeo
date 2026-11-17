package ma.pfe.stock.stockmgmt.domain.model;

public enum AlertStatus {
    /** Alerte active non traitée. */
    OPEN,
    /** Alerte prise en compte par un utilisateur (toujours active). */
    ACKNOWLEDGED,
    /** Condition disparue (réapprovisionnement). */
    RESOLVED
}
