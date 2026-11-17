package ma.pfe.stock.reporting.application.port;

import ma.pfe.stock.reporting.domain.model.AlertData;
import ma.pfe.stock.reporting.domain.model.InventoryData;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.StockLevelData;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Port : lecture des données de stock (stock-service). */
public interface StockGateway {

    List<StockLevelData> findLevels(Collection<Long> productIds);

    List<MovementData> findMovements(LocalDate from, LocalDate to, String type, Long productId);

    List<AlertData> findAlerts(boolean activeOnly);

    InventoryData findInventory(Long inventoryId);
}
