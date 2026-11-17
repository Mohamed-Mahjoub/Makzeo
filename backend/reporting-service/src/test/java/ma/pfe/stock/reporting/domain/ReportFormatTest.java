package ma.pfe.stock.reporting.domain;

import ma.pfe.stock.reporting.domain.model.ReportFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportFormatTest {

    @Test
    void parsesSupportedFormats() {
        assertThat(ReportFormat.parse(null)).isEqualTo(ReportFormat.PDF);
        assertThat(ReportFormat.parse("PDF")).isEqualTo(ReportFormat.PDF);
        assertThat(ReportFormat.parse("excel")).isEqualTo(ReportFormat.XLSX);
        assertThat(ReportFormat.parse("xlsx")).isEqualTo(ReportFormat.XLSX);
    }

    @Test
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> ReportFormat.parse("docx")).isInstanceOf(IllegalArgumentException.class);
    }
}
