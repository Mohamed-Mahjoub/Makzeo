package ma.pfe.stock.product.application.port;

/** Port : vérification de l'existence d'un fournisseur (appel à supplier-service). */
public interface SupplierDirectory {

    boolean exists(Long supplierId);
}
