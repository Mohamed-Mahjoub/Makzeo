package ma.pfe.stock.procurement.application.port;

import ma.pfe.stock.procurement.domain.model.StockInfo;

import java.util.Collection;
import java.util.Map;

/** Accès aux niveaux de stock et aux sorties (stock-service). */
public interface StockGateway {

    /** Stock actuel et consommation mensuelle moyenne sur les {@code lookbackDays} derniers jours. */
    Map<Long, StockInfo> stockSituation(Collection<Long> productIds, int lookbackDays);
}
