package ma.pfe.stock.stockmgmt.application.service;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.application.dto.MovementCommand;
import ma.pfe.stock.stockmgmt.application.dto.MovementView;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.domain.model.MovementType;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.model.StockMovement;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Cas d'utilisation : entrées / sorties de stock et historique des mouvements. */
@Service
public class StockMovementService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository movementRepository;
    private final ProductCatalog productCatalog;
    private final AlertService alertService;

    public StockMovementService(StockItemRepository stockItemRepository, StockMovementRepository movementRepository,
                                ProductCatalog productCatalog, AlertService alertService) {
        this.stockItemRepository = stockItemRepository;
        this.movementRepository = movementRepository;
        this.productCatalog = productCatalog;
        this.alertService = alertService;
    }

    /** Enregistre une entrée ou une sortie, met à jour le stock et les alertes (transaction unique). */
    @Transactional
    public MovementView record(MovementCommand cmd, String username) {
        if (cmd.type() == MovementType.ADJUSTMENT) {
            throw new BusinessRuleException("INVALID_MOVEMENT_TYPE",
                    "Les ajustements sont générés uniquement par la validation d'un inventaire");
        }
        if (cmd.quantity() <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive");
        }
        ProductSnapshot product = productCatalog.getProduct(cmd.productId());
        if (!product.active()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE",
                    "Le produit " + product.reference() + " est désactivé : mouvement refusé");
        }
        StockItem item = loadForUpdate(product);
        int before = cmd.type() == MovementType.IN ? item.increase(cmd.quantity()) : item.decrease(cmd.quantity());
        StockItem saved = stockItemRepository.save(item);
        StockMovement movement = movementRepository.save(new StockMovement(null, product.id(), product.reference(),
                product.name(), cmd.type(), cmd.quantity(), before, saved.getQuantity(),
                cmd.type() == MovementType.IN ? cmd.supplierId() : null, trim(cmd.documentReference()),
                trim(cmd.reason()), null, username, Instant.now()));
        alertService.evaluate(saved);
        return MovementView.from(movement);
    }

    /**
     * Ajuste le stock à la quantité comptée lors d'un inventaire.
     * Retourne {@code null} si aucun écart.
     */
    @Transactional
    public MovementView adjust(ProductSnapshot product, int countedQuantity, Long inventoryId, String reason,
                               String username) {
        StockItem item = loadForUpdate(product);
        int before = item.adjustTo(countedQuantity);
        StockItem saved = stockItemRepository.save(item);
        if (before == countedQuantity) {
            alertService.evaluate(saved);
            return null;
        }
        StockMovement movement = movementRepository.save(new StockMovement(null, product.id(), product.reference(),
                product.name(), MovementType.ADJUSTMENT, Math.abs(countedQuantity - before), before,
                countedQuantity, null, "INV-" + inventoryId, reason, inventoryId, username, Instant.now()));
        alertService.evaluate(saved);
        return MovementView.from(movement);
    }

    @Transactional(readOnly = true)
    public PageResult<MovementView> search(StockMovementRepository.MovementCriteria criteria, int page, int size) {
        return movementRepository.search(criteria, page, size).map(MovementView::from);
    }

    private StockItem loadForUpdate(ProductSnapshot product) {
        StockItem item = stockItemRepository.lockOrCreate(product);
        item.refreshProduct(product);
        return item;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
