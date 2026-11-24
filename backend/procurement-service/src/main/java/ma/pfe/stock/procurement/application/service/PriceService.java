package ma.pfe.stock.procurement.application.service;

import ma.pfe.stock.procurement.application.dto.ImportResult;
import ma.pfe.stock.procurement.application.dto.PriceHistoryView;
import ma.pfe.stock.procurement.application.port.CatalogGateway;
import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PricePrediction;
import ma.pfe.stock.procurement.domain.model.PriceSource;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.domain.port.PricePointRepository;
import ma.pfe.stock.procurement.domain.port.PricePredictionRepository;
import ma.pfe.stock.procurement.domain.service.PriceForecaster;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Historique des prix et prédictions. */
@Service
public class PriceService {

    static final List<PriceSource> PAID_SOURCES = List.of(PriceSource.PURCHASE);
    private static final int MAX_IMPORT_LINES = 20_000;
    /** Taille maximale du catalogue parcouru (catalogues de démonstration de 10 000 articles et plus). */
    static final int CATALOG_SCAN_LIMIT = 50_000;
    /** Sources qui constituent un historique exploitable pour la prévision (hors simple prix catalogue). */
    static final List<PriceSource> HISTORY_SOURCES = List.of(PriceSource.PURCHASE, PriceSource.QUOTE, PriceSource.MARKET);
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PricePointRepository points;
    private final PricePredictionRepository predictions;
    private final CatalogGateway catalog;
    private final ProcurementProperties properties;
    private final ZoneId zone;

    public PriceService(PricePointRepository points, PricePredictionRepository predictions, CatalogGateway catalog,
                        ProcurementProperties properties, ZoneId zone) {
        this.points = points;
        this.predictions = predictions;
        this.catalog = catalog;
        this.properties = properties;
        this.zone = zone;
    }

    public PriceHistoryView history(Long productId) {
        return new PriceHistoryView(productId, points.findByProduct(productId),
                predictions.findByProduct(productId).orElse(null));
    }

    public List<PricePrediction> predictions() {
        return predictions.findAll().stream()
                .sorted(Comparator.comparing(PricePrediction::changePct).reversed())
                .toList();
    }

    public Optional<PricePrediction> prediction(Long productId) {
        return predictions.findByProduct(productId);
    }

    /** Articles disposant d'un historique de prix (achats, devis ou prix du marché). */
    public java.util.Set<Long> productIdsWithHistory() {
        return points.productIdsWithSources(HISTORY_SOURCES);
    }

    /**
     * Recalcule la prédiction statistique d'un article en conservant le dernier signal marché relevé
     * par la veille (aucun appel à l'IA).
     */
    public Optional<PricePrediction> repredict(ProductInfo product) {
        Double signal = predictions.findByProduct(product.id())
                .map(PricePrediction::marketSignalPct)
                .map(BigDecimal::doubleValue)
                .orElse(null);
        return predict(product, signal);
    }

    /** Dernier prix réellement payé, sinon prix d'achat de la fiche produit. */
    public BigDecimal referencePrice(ProductInfo product) {
        return points.findLatest(product.id(), PAID_SOURCES).map(PricePoint::unitPrice)
                .orElse(product.purchasePrice() != null && product.purchasePrice().signum() > 0
                        ? product.purchasePrice() : null);
    }

    public PricePoint record(Long productId, BigDecimal unitPrice, LocalDate date, PriceSource source,
                             String supplierName, Integer quantity) {
        return points.save(new PricePoint(null, productId, unitPrice, date, source, supplierName, quantity));
    }

    /** Relevé mensuel du prix d'achat de la fiche produit, pour construire l'historique au fil du temps. */
    public void snapshotCatalogPrice(ProductInfo product) {
        if (product.purchasePrice() == null || product.purchasePrice().signum() <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(zone);
        Optional<PricePoint> last = points.findLatest(product.id(), List.of(PriceSource.CATALOG));
        boolean sameMonth = last.map(p -> p.observedOn().getYear() == today.getYear()
                && p.observedOn().getMonth() == today.getMonth()).orElse(false);
        if (!sameMonth) {
            record(product.id(), product.purchasePrice(), today, PriceSource.CATALOG, null, null);
        }
    }

    /** Calcule et enregistre la prédiction d'un article. */
    public Optional<PricePrediction> predict(ProductInfo product, Double marketSignalPct) {
        PriceForecaster.Settings settings = new PriceForecaster.Settings(properties.forecastHorizonMonths(),
                properties.annualInflationPct(), properties.marketSignalWeight());
        // Les devis sont des offres, pas des transactions : ils servent à la comparaison et au signal marché,
        // mais pas à la série de prix (un mois de devis mêlant plusieurs fournisseurs fausserait tendance
        // et saisonnalité).
        List<PricePoint> series = points.findByProduct(product.id()).stream()
                .filter(p -> p.source() != PriceSource.QUOTE)
                .toList();
        return PriceForecaster.forecast(series, LocalDate.now(zone), settings, marketSignalPct)
                .map(f -> predictions.upsert(new PricePrediction(null, product.id(), product.reference(),
                        product.name(), Instant.now(), f.currentPrice(), f.predictedPrice(), f.changePct(),
                        f.horizonDays(), f.confidence(), f.level(), f.monthlyTrendPct(), f.seasonalityPct(),
                        f.inflationPct(), f.marketSignalPct(), f.sampleSize(), f.rationale())));
    }

    /**
     * Import de l'historique d'achats, une ligne par achat :
     * {@code date;référence;fournisseur;quantité;prix unitaire HT} (séparateur ; ou ,
     * date AAAA-MM-JJ ou JJ/MM/AAAA, décimales avec un point ou une virgule si le séparateur est ;).
     */
    public ImportResult importPurchases(String csv) {
        Map<String, ProductInfo> byReference = catalog.activeProducts(CATALOG_SCAN_LIMIT).stream()
                .collect(Collectors.toMap(p -> p.reference().toUpperCase(), Function.identity(), (a, b) -> a));
        List<String> rejected = new ArrayList<>();
        Map<Long, ProductInfo> touched = new java.util.LinkedHashMap<>();
        int imported = 0;
        String[] lines = csv == null ? new String[0] : csv.split("\\r?\\n");
        if (lines.length > MAX_IMPORT_LINES) {
            throw new IllegalArgumentException("Import limité à " + MAX_IMPORT_LINES + " lignes");
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || (i == 0 && !Character.isDigit(line.charAt(0)))) {
                continue; // ligne vide ou en-tête
            }
            String separator = line.contains(";") ? ";" : ",";
            String[] f = line.split(separator, -1);
            if (f.length < 5) {
                rejected.add("Ligne " + (i + 1) + " : 5 colonnes attendues");
                continue;
            }
            try {
                LocalDate date = parseDate(f[0].trim());
                ProductInfo product = byReference.get(f[1].trim().toUpperCase());
                if (product == null) {
                    rejected.add("Ligne " + (i + 1) + " : référence inconnue " + f[1].trim());
                    continue;
                }
                String supplier = f[2].trim().isEmpty() ? null : f[2].trim();
                int qty = Integer.parseInt(f[3].trim());
                BigDecimal price = new BigDecimal(f[4].trim().replace(" ", "").replace(",", "."));
                record(product.id(), price, date, PriceSource.PURCHASE, supplier, qty);
                touched.putIfAbsent(product.id(), product);
                imported++;
            } catch (DateTimeParseException | NumberFormatException e) {
                rejected.add("Ligne " + (i + 1) + " : valeur illisible (" + e.getMessage() + ")");
            } catch (IllegalArgumentException e) {
                rejected.add("Ligne " + (i + 1) + " : " + e.getMessage());
            }
            if (rejected.size() > 200) {
                rejected.add("Trop d'erreurs : import interrompu");
                break;
            }
        }
        // prédictions à jour dès l'import, sans attendre la veille mensuelle
        touched.values().forEach(this::repredict);
        return new ImportResult(imported, rejected);
    }

    private static LocalDate parseDate(String value) {
        return value.contains("/") ? LocalDate.parse(value, DATE_FR) : LocalDate.parse(value);
    }
}
