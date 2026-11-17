package ma.pfe.stock.stockmgmt.application.service;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.application.dto.InventoryView;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.domain.model.Inventory;
import ma.pfe.stock.stockmgmt.domain.model.InventoryLine;
import ma.pfe.stock.stockmgmt.domain.model.InventoryStatus;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.port.InventoryRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cas d'utilisation : inventaire physique.
 * La quantité théorique d'une ligne est figée au moment du comptage ; à la validation,
 * le stock est aligné sur la quantité comptée (mouvement ADJUSTMENT pour chaque écart).
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductCatalog productCatalog;
    private final StockMovementService movementService;

    public InventoryService(InventoryRepository inventoryRepository, StockItemRepository stockItemRepository,
                            ProductCatalog productCatalog, StockMovementService movementService) {
        this.inventoryRepository = inventoryRepository;
        this.stockItemRepository = stockItemRepository;
        this.productCatalog = productCatalog;
        this.movementService = movementService;
    }

    @Transactional
    public InventoryView open(String name, String notes, String username) {
        return InventoryView.from(inventoryRepository.save(Inventory.open(name, notes, username)), true);
    }

    @Transactional
    public InventoryView recordCount(Long inventoryId, Long productId, int countedQuantity, String username) {
        Inventory inventory = get(inventoryId);
        ProductSnapshot product = productCatalog.getProduct(productId);
        int expected = stockItemRepository.findByProductId(productId).map(StockItem::getQuantity).orElse(0);
        inventory.recordCount(product, expected, countedQuantity, username);
        return InventoryView.from(inventoryRepository.save(inventory), true);
    }

    @Transactional
    public InventoryView removeCount(Long inventoryId, Long productId) {
        Inventory inventory = get(inventoryId);
        inventory.removeCount(productId);
        return InventoryView.from(inventoryRepository.save(inventory), true);
    }

    @Transactional
    public InventoryView validate(Long inventoryId, String username) {
        Inventory inventory = get(inventoryId);
        inventory.validate(username);
        List<InventoryLine> lines = inventory.getLines();
        Map<Long, ProductSnapshot> products = productCatalog.getProducts(
                        lines.stream().map(InventoryLine::productId).toList()).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        for (InventoryLine line : lines) {
            ProductSnapshot product = products.get(line.productId());
            if (product == null) {
                throw new BusinessRuleException("PRODUCT_NOT_FOUND", "Produit " + line.productReference()
                        + " introuvable dans le catalogue : validation de l'inventaire impossible");
            }
            movementService.adjust(product, line.countedQuantity(), inventory.getId(),
                    "Inventaire : " + inventory.getName(), username);
        }
        return InventoryView.from(inventoryRepository.save(inventory), true);
    }

    @Transactional
    public InventoryView cancel(Long inventoryId, String username) {
        Inventory inventory = get(inventoryId);
        inventory.cancel(username);
        return InventoryView.from(inventoryRepository.save(inventory), true);
    }

    @Transactional(readOnly = true)
    public InventoryView findById(Long id) {
        return InventoryView.from(get(id), true);
    }

    @Transactional(readOnly = true)
    public PageResult<InventoryView> search(InventoryStatus status, int page, int size) {
        return inventoryRepository.search(status, page, size).map(inv -> InventoryView.from(inv, false));
    }

    private Inventory get(Long id) {
        return inventoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Inventaire", id));
    }
}
