package ma.pfe.stock.procurement.infrastructure;

import ma.pfe.stock.procurement.application.dto.AiReport;
import ma.pfe.stock.procurement.application.dto.WatchRunView;
import ma.pfe.stock.procurement.domain.model.WatchRun;
import ma.pfe.stock.procurement.infrastructure.report.AiReportPdfRenderer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiReportPdfRendererTest {

    private final AiReportPdfRenderer renderer = new AiReportPdfRenderer(ZoneId.of("Africa/Tunis"));

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static AiReport.Kpis kpis() {
        return new AiReport.Kpis(2, 1, 0, d("3200.597"), d("145.130"), d("3055.464"), d("145.130"), 55, 3, 1, 50, 2,
                1, 9, 1, 0);
    }

    @Test
    void rendersCompleteReport() {
        AiReport report = new AiReport(Instant.parse("2026-11-24T18:00:00Z"), "admin", "TND", "Tunisie", false,
                kpis(),
                List.of(new AiReport.RecommendationLine("Changement de fournisseur", "À décider", "CE285A",
                                "Toner HP 85A noir original", "Tunewtec", 36, d("326.891"), d("242.017"),
                                d("3055.464"), null, Instant.now()),
                        new AiReport.RecommendationLine("Achat anticipé", "Acceptée", "SA400S37-480G",
                                "Disque SSD Kingston A400 480 Go", null, 50, d("310.084"), d("310.084"),
                                d("145.130"), LocalDate.of(2026, 12, 24), Instant.now())),
                List.of(new AiReport.PredictionLine("SA400S37-480G", "Disque SSD Kingston A400 480 Go", d("310.084"),
                        d("342.057"), d("10.31"), 90, 0.8, "Élevée", 24),
                        new AiReport.PredictionLine("X", "Article en baisse", d("10"), d("9.5"), d("-5"), 90, 0.4,
                                "Moyenne", 6)),
                List.of(new AiReport.ComparisonLine("CE285A", "Toner HP 85A", 4, "Tunewtec", "Toner HP 85A original",
                                false, d("242.017"), d("326.891"), d("25.96"), d("3055.464")),
                        new AiReport.ComparisonLine("NAV", "Rame Navigator", 5, "SpaceNet", "Rame Archer", true,
                                d("10.504"), null, null, null)),
                List.of(new AiReport.AmountLine("Tunewtec", BigDecimal.ZERO, d("3055.464"), 1),
                        new AiReport.AmountLine("MyTEK", d("145.130"), BigDecimal.ZERO, 1)),
                List.of(new AiReport.AmountLine("2026-11", d("145.130"), d("3055.464"), 2)),
                new WatchRunView(1L, "2026-11", WatchRun.Trigger.MANUAL, WatchRun.Status.COMPLETED, "admin",
                        Instant.now(), Instant.now(), 25, 3, 12, 6, 2, null));

        byte[] pdf = renderer.render(report);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(3000);
    }

    @Test
    void rendersEmptyReport() {
        AiReport report = new AiReport(Instant.now(), "consultant", "TND", "Tunisie", true,
                new AiReport.Kpis(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0,
                        0, 0, 0, 0, 0, 0, 0),
                List.of(), List.of(), List.of(), List.of(), List.of(), null);

        assertThat(new String(renderer.render(report), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
