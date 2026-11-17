package ma.pfe.stock.reporting.application;

import ma.pfe.stock.reporting.application.port.CatalogGateway;
import ma.pfe.stock.reporting.application.port.StockGateway;
import ma.pfe.stock.reporting.application.service.ReportService;
import ma.pfe.stock.reporting.domain.model.AlertData;
import ma.pfe.stock.reporting.domain.model.CurrencyInfo;
import ma.pfe.stock.reporting.domain.model.GeneratedReport;
import ma.pfe.stock.reporting.domain.model.InventoryData;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.ProductData;
import ma.pfe.stock.reporting.domain.model.ReportFormat;
import ma.pfe.stock.reporting.domain.model.StockLevelData;
import ma.pfe.stock.reporting.infrastructure.render.ExcelReportRenderer;
import ma.pfe.stock.reporting.infrastructure.render.PdfReportRenderer;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Génère de vrais fichiers PDF / Excel à partir de données simulées. */
class ReportServiceTest {

    private ReportService service;
    private CurrencyInfo currency = CurrencyInfo.DEFAULT;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("Africa/Casablanca");
        CatalogGateway catalog = (categoryId, active) -> List.of(
                new ProductData(1L, "PRD-1", "Papier A4", "Bureau", "2000000000077", "PAQUET",
                        new BigDecimal("32.50"), new BigDecimal("45.00"), 10, null, true),
                new ProductData(2L, "PRD-2", "Stylo", "Bureau", "2000000000084", "PIECE",
                        new BigDecimal("2.00"), new BigDecimal("4.00"), 50, null, true),
                new ProductData(3L, "PRD-3", "Agrafeuse", "Bureau", "2000000000091", "PIECE",
                        new BigDecimal("45.00"), new BigDecimal("70.00"), 2, 20, true));
        StockGateway stock = new StockGateway() {
            public List<StockLevelData> findLevels(java.util.Collection<Long> ids) {
                return List.of(new StockLevelData(1L, 100, "OK"), new StockLevelData(2L, 20, "LOW"));
            }

            public List<MovementData> findMovements(LocalDate from, LocalDate to, String type, Long productId) {
                return List.of(new MovementData(1L, 1L, "PRD-1", "Papier A4", "IN", "Entrée", 100, 100, 0, 100, "BL-1",
                        null, "mag", Instant.now()));
            }

            public List<AlertData> findAlerts(boolean activeOnly) {
                return List.of(new AlertData(1L, "PRD-3", "Agrafeuse", "RUPTURE", "Rupture de stock", "OPEN", 0, 2,
                        Instant.now()));
            }

            public InventoryData findInventory(Long id) {
                return new InventoryData(id, "Annuel", "VALIDATED", "mag", Instant.now(), "chef", Instant.now(),
                        List.of(new InventoryData.Line("PRD-1", "Papier A4", 100, 98, -2, "mag")));
            }
        };
        service = new ReportService(catalog, stock, () -> currency,
                List.of(new PdfReportRenderer(zone), new ExcelReportRenderer(zone)), zone);
    }

    @Test
    void stockReportPdfIsAValidPdf() {
        GeneratedReport report = service.stockReport(ReportFormat.PDF, null, false, "admin");

        assertThat(new String(report.content(), 0, 5)).isEqualTo("%PDF-");
        assertThat(report.fileName()).startsWith("etat-stock-").endsWith(".pdf");
        assertThat(report.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void stockReportExcelContainsValuedLines() throws Exception {
        GeneratedReport report = service.stockReport(ReportFormat.XLSX, null, false, "admin");

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            int header = 4; // titre + 2 sous-titres + ligne vide
            assertThat(sheet.getRow(header).getCell(0).getStringCellValue()).isEqualTo("Référence");
            assertThat(sheet.getRow(header + 1).getCell(0).getStringCellValue()).isEqualTo("PRD-1");
            assertThat(sheet.getRow(header + 1).getCell(8).getNumericCellValue()).isEqualTo(3250.0);
            assertThat(sheet.getRow(header + 3).getCell(6).getStringCellValue()).isEqualTo("Rupture");
        }
    }

    @Test
    void stockReportUsesApplicationCurrencyAndItsDecimals() throws Exception {
        currency = new CurrencyInfo("TND", 3);
        GeneratedReport report = service.stockReport(ReportFormat.XLSX, null, false, "admin");

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(report.content()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(4).getCell(7).getStringCellValue()).isEqualTo("Prix achat (TND)");
            assertThat(sheet.getRow(4).getCell(8).getStringCellValue()).isEqualTo("Valeur (TND)");
            assertThat(sheet.getRow(5).getCell(8).getCellStyle().getDataFormatString()).isEqualTo("#,##0.000");
        }
        assertThat(new String(service.stockReport(ReportFormat.PDF, null, false, "admin").content(), 0, 5))
                .isEqualTo("%PDF-");
    }

    @Test
    void otherReportsAreGeneratedInBothFormats() {
        for (ReportFormat format : ReportFormat.values()) {
            assertThat(service.movementsReport(format, LocalDate.now().minusDays(7), LocalDate.now(), null, null,
                    "admin").content()).isNotEmpty();
            assertThat(service.alertsReport(format, true, "admin").content()).isNotEmpty();
            assertThat(service.inventoryReport(format, 3L, "admin").fileName()).startsWith("inventaire-3-");
        }
    }

    @Test
    void rejectsInvertedPeriod() {
        assertThatThrownBy(() -> service.movementsReport(ReportFormat.PDF, LocalDate.now(),
                LocalDate.now().minusDays(1), null, null, "admin")).isInstanceOf(IllegalArgumentException.class);
    }
}
