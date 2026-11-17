package ma.pfe.stock.stockmgmt.application.service;

import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.stockmgmt.application.dto.AlertView;
import ma.pfe.stock.stockmgmt.application.port.ProductCatalog;
import ma.pfe.stock.stockmgmt.domain.model.AlertStatus;
import ma.pfe.stock.stockmgmt.domain.model.AlertType;
import ma.pfe.stock.stockmgmt.domain.model.ProductSnapshot;
import ma.pfe.stock.stockmgmt.domain.model.StockAlert;
import ma.pfe.stock.stockmgmt.domain.model.StockItem;
import ma.pfe.stock.stockmgmt.domain.port.StockAlertRepository;
import ma.pfe.stock.stockmgmt.domain.port.StockItemRepository;
import ma.pfe.stock.stockmgmt.domain.service.AlertPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cas d'utilisation : alertes de rupture et de seuil minimum. */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final StockAlertRepository alertRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductCatalog productCatalog;

    public AlertService(StockAlertRepository alertRepository, StockItemRepository stockItemRepository,
                        ProductCatalog productCatalog) {
        this.alertRepository = alertRepository;
        this.stockItemRepository = stockItemRepository;
        this.productCatalog = productCatalog;
    }

    /**
     * Réévalue les alertes d'un produit après modification de son stock :
     * création, mise à jour ou résolution automatique.
     */
    @Transactional
    public void evaluate(StockItem item) {
        Optional<AlertType> expected = AlertPolicy.evaluate(item);
        List<StockAlert> active = alertRepository.findActiveByProductId(item.getProductId());
        boolean alreadyRaised = false;
        for (StockAlert alert : active) {
            if (expected.isPresent() && alert.getType() == expected.get()) {
                alert.updateQuantity(item.getQuantity());
                alreadyRaised = true;
            } else {
                alert.resolve();
            }
            alertRepository.save(alert);
        }
        if (expected.isPresent() && !alreadyRaised) {
            StockAlert alert = alertRepository.save(StockAlert.raise(item, expected.get()));
            log.warn("ALERTE {}", alert.message());
        }
    }

    @Transactional(readOnly = true)
    public PageResult<AlertView> search(AlertStatus status, AlertType type, Boolean activeOnly, int page, int size) {
        return alertRepository.search(status, type, activeOnly, page, size).map(AlertView::from);
    }

    @Transactional
    public AlertView acknowledge(Long id, String username) {
        StockAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte", id));
        alert.acknowledge(username);
        return AlertView.from(alertRepository.save(alert));
    }

    /**
     * Rafraîchit les seuils depuis le catalogue puis réévalue toutes les alertes
     * (à lancer après une modification des seuils minimum).
     */
    @Transactional
    public int recomputeAll() {
        List<StockItem> items = stockItemRepository.findAll();
        if (items.isEmpty()) {
            return 0;
        }
        Map<Long, ProductSnapshot> products = productCatalog.getProducts(
                        items.stream().map(StockItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(ProductSnapshot::id, Function.identity()));
        for (StockItem item : items) {
            ProductSnapshot product = products.get(item.getProductId());
            if (product != null) {
                item.refreshProduct(product);
                stockItemRepository.save(item);
            }
            evaluate(item);
        }
        return items.size();
    }
}
