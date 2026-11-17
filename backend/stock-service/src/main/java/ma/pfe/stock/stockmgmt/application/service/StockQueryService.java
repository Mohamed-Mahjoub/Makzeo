package ma.pfe.stock.stockmgmt.application.service;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.application.dto.DashboardView;
import ma.pfe.stock.stockmgmt.application.dto.StockLevelView;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockStatus;
import ma.pfe.stock.stockmgmt.domain.port.StockAlertRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

/** Cas d'utilisation : consultation des niveaux de stock et indicateurs. */
@Service
public class StockQueryService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository movementRepository;
    private final StockAlertRepository alertRepository;
    private final ProductCatalog productCatalog;
    private final ZoneId zoneId;

    public StockQueryService(StockItemRepository stockItemRepository, StockMovementRepository movementRepository,
                             StockAlertRepository alertRepository, ProductCatalog productCatalog, ZoneId zoneId) {
        this.stockItemRepository = stockItemRepository;
        this.movementRepository = movementRepository;
        this.alertRepository = alertRepository;
        this.productCatalog = productCatalog;
        this.zoneId = zoneId;
    }

    /** Niveau de stock d'un produit (0 si aucun mouvement n'a encore été enregistré). */
    @Transactional(readOnly = true)
    public StockLevelView level(Long productId) {
        return stockItemRepository.findByProductId(productId)
                .map(StockLevelView::from)
                .orElseGet(() -> {
                    ProductSnapshot product = productCatalog.getProduct(productId);
                    return StockLevelView.from(StockItem.empty(product));
                });
    }

    /** Niveaux de stock pour une liste de produits (produits sans mouvement exclus). */
    @Transactional(readOnly = true)
    public List<StockLevelView> levels(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return stockItemRepository.findByProductIds(productIds).stream().map(StockLevelView::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResult<StockLevelView> search(String keyword, StockStatus status, int page, int size) {
        return stockItemRepository.search(keyword, status, page, size).map(StockLevelView::from);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard() {
        Instant startOfDay = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant();
        return new DashboardView(
                stockItemRepository.count(),
                stockItemRepository.sumQuantity(),
                stockItemRepository.countByStatus(StockStatus.OUT_OF_STOCK),
                stockItemRepository.countByStatus(StockStatus.LOW),
                alertRepository.countActive(AlertType.RUPTURE),
                alertRepository.countActive(AlertType.SEUIL_MIN),
                movementRepository.countSince(startOfDay, MovementType.IN),
                movementRepository.countSince(startOfDay, MovementType.OUT),
                movementRepository.countSince(startOfDay, MovementType.ADJUSTMENT));
    }
}
