package ma.pfe.stock.stockmgmt.domain.service;

import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;

import java.util.Optional;

/**
 * Service de domaine : règle de déclenchement des alertes.
 * <ul>
 *   <li>quantité = 0 : RUPTURE</li>
 *   <li>0 &lt; quantité &lt;= seuil minimum : SEUIL_MIN</li>
 *   <li>sinon : aucune alerte</li>
 * </ul>
 */
public final class AlertPolicy {

    private AlertPolicy() {
    }

    public static Optional<AlertType> evaluate(StockItem item) {
        return switch (item.status()) {
            case OUT_OF_STOCK -> Optional.of(AlertType.RUPTURE);
            case LOW -> Optional.of(AlertType.SEUIL_MIN);
            case OK -> Optional.empty();
        };
    }
}
