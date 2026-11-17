package ma.pfe.stock.reporting.application.service;

import ma.pfe.stock.reporting.application.port.CatalogGateway;
import ma.pfe.stock.reporting.application.port.ReportRenderer;
import ma.pfe.stock.reporting.application.port.SettingsGateway;
import ma.pfe.stock.reporting.application.port.StockGateway;
import ma.pfe.stock.reporting.domain.model.AlertData;
import ma.pfe.stock.reporting.domain.model.CurrencyInfo;
import ma.pfe.stock.reporting.domain.model.GeneratedReport;
import ma.pfe.stock.reporting.domain.model.InventoryData;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.ProductData;
import ma.pfe.stock.reporting.domain.model.ReportFormat;
import ma.pfe.stock.reporting.domain.model.StockLevelData;
import ma.pfe.stock.reporting.domain.model.TableReport;
import ma.pfe.stock.reporting.domain.model.TableReport.Column;
import ma.pfe.stock.reporting.domain.model.TableReport.ColumnType;
import ma.pfe.stock.reporting.domain.model.TableReport.Highlight;
import ma.pfe.stock.reporting.domain.model.TableReport.Row;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Cas d'utilisation : construction des rapports (état du stock, mouvements, alertes, inventaire). */
@Service
public class ReportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private static final DateTimeFormatter HUMAN_TS = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm");
    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CatalogGateway catalogGateway;
    private final StockGateway stockGateway;
    private final SettingsGateway settingsGateway;
    private final Map<ReportFormat, ReportRenderer> renderers = new EnumMap<>(ReportFormat.class);
    private final ZoneId zoneId;

    public ReportService(CatalogGateway catalogGateway, StockGateway stockGateway, SettingsGateway settingsGateway,
                         List<ReportRenderer> renderers, ZoneId zoneId) {
        this.catalogGateway = catalogGateway;
        this.stockGateway = stockGateway;
        this.settingsGateway = settingsGateway;
        renderers.forEach(r -> this.renderers.put(r.format(), r));
        this.zoneId = zoneId;
    }

    /** État du stock valorisé (prix d'achat) de tous les produits. */
    public GeneratedReport stockReport(ReportFormat format, Long categoryId, boolean includeInactive, String user) {
        CurrencyInfo currency = settingsGateway.currency();
        List<ProductData> products = catalogGateway.findAllProducts(categoryId, includeInactive ? null : Boolean.TRUE);
        Map<Long, StockLevelData> levels = stockGateway.findLevels(products.stream().map(ProductData::id).toList())
                .stream().collect(Collectors.toMap(StockLevelData::productId, Function.identity(), (a, b) -> a));

        List<Row> rows = new ArrayList<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        long totalQuantity = 0;
        int ruptures = 0;
        int lows = 0;
        for (ProductData p : products.stream().sorted(Comparator.comparing(ProductData::reference)).toList()) {
            StockLevelData level = levels.get(p.id());
            int qty = level == null ? 0 : level.quantity();
            String status = qty <= 0 ? "OUT_OF_STOCK" : (qty <= p.minStock() ? "LOW" : "OK");
            BigDecimal price = currency.round(p.purchasePrice());
            BigDecimal value = currency.round(price.multiply(BigDecimal.valueOf(qty)));
            totalValue = totalValue.add(value);
            totalQuantity += qty;
            Highlight highlight = switch (status) {
                case "OUT_OF_STOCK" -> {
                    ruptures++;
                    yield Highlight.DANGER;
                }
                case "LOW" -> {
                    lows++;
                    yield Highlight.WARNING;
                }
                default -> Highlight.NONE;
            };
            rows.add(new Row(List.of(p.reference(), p.name(), nullToDash(p.categoryName()), nullToDash(p.unit()),
                    qty, p.minStock(), statusLabel(status), price, value), highlight));
        }
        TableReport report = new TableReport(
                "État du stock",
                List.of(generatedBy(user), categoryId == null ? "Toutes catégories" : "Catégorie n° " + categoryId),
                List.of(text("Référence", 1.3f), text("Désignation", 2.6f), text("Catégorie", 1.5f),
                        text("Unité", 0.8f), integer("Quantité", 0.9f), integer("Seuil min.", 0.9f),
                        text("État", 1.1f), decimal("Prix achat (" + currency.code() + ")", 1.1f),
                        decimal("Valeur (" + currency.code() + ")", 1.2f)),
                rows,
                List.of("Nombre de produits : " + products.size(),
                        "Quantité totale en stock : " + totalQuantity,
                        "Valeur totale du stock : " + currency.round(totalValue).toPlainString() + " " + currency.code(),
                        "Produits en rupture : " + ruptures + " — Produits sous le seuil : " + lows),
                currency.fractionDigits());
        return render(report, format, "etat-stock");
    }

    /** Historique des mouvements sur une période. */
    public GeneratedReport movementsReport(ReportFormat format, LocalDate from, LocalDate to, String type,
                                           Long productId, String user) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La date de début doit précéder la date de fin");
        }
        List<MovementData> movements = stockGateway.findMovements(from, to, type, productId);
        long in = 0;
        long out = 0;
        List<Row> rows = new ArrayList<>();
        for (MovementData m : movements) {
            if (m.signedQuantity() >= 0) {
                in += m.signedQuantity();
            } else {
                out -= m.signedQuantity();
            }
            Highlight h = switch (m.type()) {
                case "IN" -> Highlight.SUCCESS;
                case "OUT" -> Highlight.NONE;
                default -> Highlight.WARNING;
            };
            rows.add(new Row(List.of(m.occurredAt(), m.productReference(), m.productName(), m.typeLabel(),
                    m.signedQuantity(), m.quantityBefore(), m.quantityAfter(), nullToDash(m.documentReference()),
                    m.performedBy()), h));
        }
        String period;
        if (from == null && to == null) {
            period = "Période : toutes les dates";
        } else if (from == null) {
            period = "Période : jusqu'au " + to.format(HUMAN_DATE);
        } else if (to == null) {
            period = "Période : depuis le " + from.format(HUMAN_DATE);
        } else {
            period = "Période : du " + from.format(HUMAN_DATE) + " au " + to.format(HUMAN_DATE);
        }
        TableReport report = new TableReport(
                "Historique des mouvements de stock",
                List.of(generatedBy(user), period + (type == null ? "" : " — Type : " + type)),
                List.of(dateTime("Date", 1.3f), text("Référence", 1.2f), text("Désignation", 2.4f),
                        text("Type", 1.3f), integer("Quantité", 0.9f), integer("Avant", 0.8f),
                        integer("Après", 0.8f), text("Document", 1.2f), text("Utilisateur", 1.1f)),
                rows,
                List.of("Nombre de mouvements : " + movements.size(),
                        "Total des quantités entrées : " + in,
                        "Total des quantités sorties : " + out));
        return render(report, format, "mouvements-stock");
    }

    /** Alertes de stock (actives ou toutes). */
    public GeneratedReport alertsReport(ReportFormat format, boolean activeOnly, String user) {
        List<AlertData> alerts = stockGateway.findAlerts(activeOnly);
        List<Row> rows = alerts.stream().map(a -> new Row(List.<Object>of(a.createdAt(), a.productReference(),
                        a.productName(), a.typeLabel(), alertStatusLabel(a.status()), a.quantity(), a.threshold()),
                "RUPTURE".equals(a.type()) ? Highlight.DANGER : Highlight.WARNING)).toList();
        long ruptures = alerts.stream().filter(a -> "RUPTURE".equals(a.type())).count();
        TableReport report = new TableReport(
                activeOnly ? "Alertes de stock actives" : "Historique des alertes de stock",
                List.of(generatedBy(user)),
                List.of(dateTime("Date", 1.3f), text("Référence", 1.2f), text("Désignation", 2.6f),
                        text("Type", 1.6f), text("Statut", 1.1f), integer("Quantité", 0.9f),
                        integer("Seuil", 0.8f)),
                rows,
                List.of("Nombre d'alertes : " + alerts.size(),
                        "Ruptures : " + ruptures + " — Seuils minimum : " + (alerts.size() - ruptures)));
        return render(report, format, "alertes-stock");
    }

    /** Procès-verbal d'inventaire avec écarts. */
    public GeneratedReport inventoryReport(ReportFormat format, Long inventoryId, String user) {
        InventoryData inv = stockGateway.findInventory(inventoryId);
        int positive = 0;
        int negative = 0;
        List<Row> rows = new ArrayList<>();
        for (InventoryData.Line l : inv.lines()) {
            if (l.difference() > 0) {
                positive += l.difference();
            } else {
                negative -= l.difference();
            }
            rows.add(new Row(List.of(l.productReference(), l.productName(), l.expectedQuantity(),
                    l.countedQuantity(), l.difference(), nullToDash(l.countedBy())),
                    l.difference() == 0 ? Highlight.NONE : Highlight.WARNING));
        }
        long discrepancies = inv.lines().stream().filter(l -> l.difference() != 0).count();
        TableReport report = new TableReport(
                "Inventaire n° " + inv.id() + " — " + inv.name(),
                List.of(generatedBy(user), "Statut : " + inventoryStatusLabel(inv.status())
                        + " — Ouvert par " + inv.createdBy() + " le " + inv.createdAt().atZone(zoneId).format(HUMAN_TS)
                        + (inv.closedAt() == null ? "" : " — Clôturé par " + inv.closedBy() + " le "
                        + inv.closedAt().atZone(zoneId).format(HUMAN_TS))),
                List.of(text("Référence", 1.3f), text("Désignation", 2.8f), integer("Théorique", 1f),
                        integer("Compté", 1f), integer("Écart", 0.9f), text("Compté par", 1.2f)),
                rows,
                List.of("Produits comptés : " + inv.lines().size(),
                        "Lignes avec écart : " + discrepancies,
                        "Écarts positifs : +" + positive + " — Écarts négatifs : -" + negative));
        return render(report, format, "inventaire-" + inv.id());
    }

    private GeneratedReport render(TableReport report, ReportFormat format, String baseName) {
        ReportRenderer renderer = renderers.get(format);
        if (renderer == null) {
            throw new IllegalArgumentException("Aucun générateur disponible pour le format " + format);
        }
        String fileName = baseName + "-" + ZonedDateTime.now(zoneId).format(FILE_TS) + "." + format.extension();
        return new GeneratedReport(fileName, format.contentType(), renderer.render(report));
    }

    private String generatedBy(String user) {
        return "Généré le " + ZonedDateTime.now(zoneId).format(HUMAN_TS) + " par " + user;
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "OUT_OF_STOCK" -> "Rupture";
            case "LOW" -> "Stock faible";
            default -> "Normal";
        };
    }

    private static String alertStatusLabel(String status) {
        return switch (status) {
            case "OPEN" -> "Ouverte";
            case "ACKNOWLEDGED" -> "Prise en compte";
            case "RESOLVED" -> "Résolue";
            default -> status;
        };
    }

    private static String inventoryStatusLabel(String status) {
        return switch (status) {
            case "IN_PROGRESS" -> "En cours";
            case "VALIDATED" -> "Validé";
            case "CANCELLED" -> "Annulé";
            default -> status;
        };
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static Column text(String header, float weight) {
        return new Column(header, ColumnType.TEXT, weight);
    }

    private static Column integer(String header, float weight) {
        return new Column(header, ColumnType.INTEGER, weight);
    }

    private static Column decimal(String header, float weight) {
        return new Column(header, ColumnType.DECIMAL, weight);
    }

    private static Column dateTime(String header, float weight) {
        return new Column(header, ColumnType.DATETIME, weight);
    }
}
