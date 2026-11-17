package ma.pfe.stock.common.security;

/**
 * Rôles applicatifs et expressions d'autorisation réutilisables dans {@code @PreAuthorize}.
 */
public final class Roles {

    /** Administrateur : gestion des utilisateurs + tous les droits. */
    public static final String ADMIN = "ADMIN";
    /** Gestionnaire de stock : catalogue, fournisseurs, stock, rapports. */
    public static final String GESTIONNAIRE = "GESTIONNAIRE";
    /** Magasinier : entrées / sorties / inventaires. */
    public static final String MAGASINIER = "MAGASINIER";
    /** Consultation seule. */
    public static final String CONSULTANT = "CONSULTANT";

    public static final String IS_ADMIN = "hasRole('ADMIN')";
    public static final String CAN_READ = "hasAnyRole('ADMIN','GESTIONNAIRE','MAGASINIER','CONSULTANT')";
    public static final String CAN_MANAGE_CATALOG = "hasAnyRole('ADMIN','GESTIONNAIRE')";
    public static final String CAN_MOVE_STOCK = "hasAnyRole('ADMIN','GESTIONNAIRE','MAGASINIER')";
    public static final String CAN_REPORT = "hasAnyRole('ADMIN','GESTIONNAIRE','CONSULTANT')";

    private Roles() {
    }
}
