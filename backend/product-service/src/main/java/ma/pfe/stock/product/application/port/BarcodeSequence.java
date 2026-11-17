package ma.pfe.stock.product.application.port;

/** Port : fournit un numéro unique pour générer les codes-barres internes. */
public interface BarcodeSequence {

    long next();
}
