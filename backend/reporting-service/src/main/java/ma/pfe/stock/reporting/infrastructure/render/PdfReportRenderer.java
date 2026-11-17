package ma.pfe.stock.reporting.infrastructure.render;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import ma.pfe.stock.reporting.application.port.ReportRenderer;
import ma.pfe.stock.reporting.domain.model.ReportFormat;
import ma.pfe.stock.reporting.domain.model.TableReport;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.util.List;

/** Adaptateur de rendu PDF (OpenPDF) : A4 paysage, en-tête, tableau, synthèse et pagination. */
@Component
public class PdfReportRenderer implements ReportRenderer {

    private static final Color PRIMARY = new Color(0x1F, 0x4E, 0x79);
    private static final Color ZEBRA = new Color(0xF3, 0xF6, 0xF9);
    private static final Color DANGER = new Color(0xF8, 0xD7, 0xDA);
    private static final Color WARNING = new Color(0xFF, 0xF3, 0xCD);
    private static final Color SUCCESS = new Color(0xD4, 0xED, 0xDA);

    private static final Font TITLE = new Font(Font.HELVETICA, 16, Font.BOLD, PRIMARY);
    private static final Font SUBTITLE = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.DARK_GRAY);
    private static final Font HEADER = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
    private static final Font CELL = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);
    private static final Font SUMMARY = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    private static final Font FOOTER = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);

    /** Nom de l'application affiché dans les pieds de page. */
    public static final String APP_NAME = "Makzeo";

    private final CellFormatter formatter;

    public PdfReportRenderer(ZoneId businessZoneId) {
        this.formatter = new CellFormatter(businessZoneId);
    }

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] render(TableReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 28, 28, 30, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new FooterEvent(report.title()));
            document.addTitle(report.title());
            document.addCreator(APP_NAME + " - reporting-service");
            document.open();

            Paragraph title = new Paragraph(report.title(), TITLE);
            title.setSpacingAfter(4);
            document.add(title);
            for (String subtitle : report.subtitles()) {
                document.add(new Paragraph(subtitle, SUBTITLE));
            }
            document.add(Chunk.NEWLINE);
            document.add(buildTable(report));

            if (!report.summary().isEmpty()) {
                Paragraph summaryTitle = new Paragraph("Synthèse", SUMMARY);
                summaryTitle.setSpacingBefore(10);
                document.add(summaryTitle);
                for (String line : report.summary()) {
                    document.add(new Paragraph("• " + line, CELL));
                }
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Erreur lors de la génération du PDF", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    private PdfPTable buildTable(TableReport report) {
        List<TableReport.Column> columns = report.columns();
        float[] widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).weight();
        }
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        for (TableReport.Column column : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(column.header(), HEADER));
            cell.setBackgroundColor(PRIMARY);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4);
            table.addCell(cell);
        }

        if (report.rows().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("Aucune donnée pour les critères sélectionnés", CELL));
            empty.setColspan(columns.size());
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setPadding(8);
            table.addCell(empty);
            return table;
        }

        int index = 0;
        for (TableReport.Row row : report.rows()) {
            Color background = switch (row.highlight()) {
                case DANGER -> DANGER;
                case WARNING -> WARNING;
                case SUCCESS -> SUCCESS;
                case NONE -> index % 2 == 1 ? ZEBRA : Color.WHITE;
            };
            for (int i = 0; i < columns.size(); i++) {
                Object value = i < row.cells().size() ? row.cells().get(i) : null;
                PdfPCell cell = new PdfPCell(new Phrase(formatter.format(value, report.decimals()), CELL));
                cell.setBackgroundColor(background);
                cell.setPadding(3);
                cell.setBorderColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(switch (columns.get(i).align()) {
                    case RIGHT -> Element.ALIGN_RIGHT;
                    case CENTER -> Element.ALIGN_CENTER;
                    case LEFT -> Element.ALIGN_LEFT;
                });
                table.addCell(cell);
            }
            index++;
        }
        return table;
    }

    /** Pied de page : titre du rapport et numéro de page. */
    private static final class FooterEvent extends PdfPageEventHelper {

        private final String title;

        private FooterEvent(String title) {
            this.title = title;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase(APP_NAME + " — " + title, FOOTER), document.left(), page.getBottom() + 18, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), FOOTER), document.right(), page.getBottom() + 18, 0);
        }
    }
}
