package ma.pfe.stock.reporting.infrastructure.web;

import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.reporting.application.service.ReportService;
import ma.pfe.stock.reporting.domain.model.GeneratedReport;
import ma.pfe.stock.reporting.domain.model.ReportFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * API de reporting : /api/reports. Paramètre {@code format} = pdf (défaut) ou xlsx.
 */
@RestController
@Tag(name = "Rapports", description = "Rapports PDF (format=pdf) et Excel (format=xlsx)")
@RequestMapping("/api/reports")
@PreAuthorize(Roles.CAN_REPORT)
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Rapport : état du stock valorisé")
    @GetMapping("/stock")
    public ResponseEntity<byte[]> stock(@RequestParam(defaultValue = "pdf") String format,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(defaultValue = "false") boolean includeInactive) {
        return toResponse(reportService.stockReport(ReportFormat.parse(format), categoryId, includeInactive,
                CurrentUser.username()));
    }

    @Operation(summary = "Rapport : historique des mouvements")
    @GetMapping("/movements")
    public ResponseEntity<byte[]> movements(
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long productId) {
        return toResponse(reportService.movementsReport(ReportFormat.parse(format), from, to, type, productId,
                CurrentUser.username()));
    }

    @Operation(summary = "Rapport : alertes de stock")
    @GetMapping("/alerts")
    public ResponseEntity<byte[]> alerts(@RequestParam(defaultValue = "pdf") String format,
                                         @RequestParam(defaultValue = "true") boolean activeOnly) {
        return toResponse(reportService.alertsReport(ReportFormat.parse(format), activeOnly, CurrentUser.username()));
    }

    @Operation(summary = "Rapport : procès-verbal d'inventaire")
    @GetMapping("/inventories/{id}")
    public ResponseEntity<byte[]> inventory(@PathVariable Long id,
                                            @RequestParam(defaultValue = "pdf") String format) {
        return toResponse(reportService.inventoryReport(ReportFormat.parse(format), id, CurrentUser.username()));
    }

    private ResponseEntity<byte[]> toResponse(GeneratedReport report) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(report.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(report.contentType()))
                .contentLength(report.content().length)
                .body(report.content());
    }
}
