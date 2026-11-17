package ma.pfe.stock.stockmgmt.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockItemRepository {

    /**
     * Retourne le niveau de stock du produit verrouillé en écriture (SELECT ... FOR UPDATE),
     * en le créant à 0 s'il n'existe pas encore. Sérialise les mouvements concurrents sur un même produit.
     */
    StockItem lockOrCreate(ProductSnapshot product);

    Optional<StockItem> findByProductId(Long productId);

    List<StockItem> findByProductIds(Collection<Long> productIds);

    List<StockItem> findAll();

    StockItem save(StockItem item);

    PageResult<StockItem> search(String keyword, StockStatus status, int page, int size);

    long count();

    long sumQuantity();

    long countByStatus(StockStatus status);
}
