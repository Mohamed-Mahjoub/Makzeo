package ma.pfe.stock.stockmgmt.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrégat "Inventaire physique" : saisie des quantités comptées puis validation,
 * qui génère des mouvements d'ajustement pour chaque écart.
 */
public class Inventory {

    private final Long id;
    private final String name;
    private final String notes;
    private InventoryStatus status;
    private final String createdBy;
    private final Instant createdAt;
    private String closedBy;
    private Instant closedAt;
    private final Map<Long, InventoryLine> lines = new LinkedHashMap<>();

    public Inventory(Long id, String name, String notes, InventoryStatus status, String createdBy, Instant createdAt,
                     String closedBy, Instant closedAt, List<InventoryLine> lines) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le libellé de l'inventaire est obligatoire");
        }
        this.id = id;
        this.name = name.trim();
        this.notes = notes;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        if (lines != null) {
            lines.forEach(l -> this.lines.put(l.productId(), l));
        }
    }

    public static Inventory open(String name, String notes, String createdBy) {
        return new Inventory(null, name, notes, InventoryStatus.IN_PROGRESS, createdBy, Instant.now(), null, null,
                List.of());
    }

    /** Enregistre (ou remplace) le comptage d'un produit. */
    public InventoryLine recordCount(ProductSnapshot product, int expectedQuantity, int countedQuantity,
                                     String countedBy) {
        requireInProgress();
        InventoryLine line = new InventoryLine(product.id(), product.reference(), product.name(), expectedQuantity,
                countedQuantity, countedBy, Instant.now());
        lines.put(product.id(), line);
        return line;
    }

    public void removeCount(Long productId) {
        requireInProgress();
        if (lines.remove(productId) == null) {
            throw new BusinessRuleException("LINE_NOT_FOUND", "Aucun comptage pour le produit " + productId);
        }
    }

    public void validate(String username) {
        requireInProgress();
        if (lines.isEmpty()) {
            throw new BusinessRuleException("EMPTY_INVENTORY", "Impossible de valider un inventaire sans comptage");
        }
        this.status = InventoryStatus.VALIDATED;
        this.closedBy = username;
        this.closedAt = Instant.now();
    }

    public void cancel(String username) {
        requireInProgress();
        this.status = InventoryStatus.CANCELLED;
        this.closedBy = username;
        this.closedAt = Instant.now();
    }

    private void requireInProgress() {
        if (status != InventoryStatus.IN_PROGRESS) {
            throw new BusinessRuleException("INVENTORY_CLOSED", "L'inventaire n'est plus modifiable (statut "
                    + status + ")");
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getNotes() { return notes; }
    public InventoryStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getClosedBy() { return closedBy; }
    public Instant getClosedAt() { return closedAt; }
    public List<InventoryLine> getLines() { return new ArrayList<>(lines.values()); }
}
