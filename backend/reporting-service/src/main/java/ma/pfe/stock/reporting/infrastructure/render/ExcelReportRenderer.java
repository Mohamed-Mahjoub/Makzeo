package ma.pfe.stock.reporting.infrastructure.render;

import ma.pfe.stock.reporting.application.port.ReportRenderer;
import ma.pfe.stock.reporting.domain.model.ReportFormat;
import ma.pfe.stock.reporting.domain.model.TableReport;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Adaptateur de rendu Excel (Apache POI, .xlsx) : cellules typées, filtres et volets figés. */
@Component
public class ExcelReportRenderer implements ReportRenderer {

    private static final int MAX_COLUMN_WIDTH_CHARS = 60;

    private final ZoneId zoneId;

    public ExcelReportRenderer(ZoneId businessZoneId) {
        this.zoneId = businessZoneId;
    }

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public byte[] render(TableReport report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook, report.decimals());
            Sheet sheet = workbook.createSheet(safeSheetName(report.title()));
            List<TableReport.Column> columns = report.columns();
            int[] widths = new int[columns.size()];

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(report.title());
            titleCell.setCellStyle(styles.title);
            if (columns.size() > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            }
            for (String subtitle : report.subtitles()) {
                Cell cell = sheet.createRow(rowIndex++).createCell(0);
                cell.setCellValue(subtitle);
                cell.setCellStyle(styles.subtitle);
            }
            rowIndex++;

            int headerRowIndex = rowIndex;
            Row header = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(styles.header);
                widths[i] = columns.get(i).header().length() + 4;
            }

            for (TableReport.Row data : report.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    Object value = i < data.cells().size() ? data.cells().get(i) : null;
                    Cell cell = row.createCell(i);
                    TableReport.ColumnType type = columns.get(i).type();
                    writeValue(cell, value);
                    cell.setCellStyle(styles.body(type, data.highlight()));
                    widths[i] = Math.max(widths[i], displayLength(value) + 2);
                }
            }
            int lastDataRow = rowIndex - 1;

            if (!report.summary().isEmpty()) {
                rowIndex++;
                Cell summaryTitle = sheet.createRow(rowIndex++).createCell(0);
                summaryTitle.setCellValue("Synthèse");
                summaryTitle.setCellStyle(styles.summaryTitle);
                for (String line : report.summary()) {
                    sheet.createRow(rowIndex++).createCell(0).setCellValue(line);
                }
            }

            for (int i = 0; i < columns.size(); i++) {
                sheet.setColumnWidth(i, Math.min(widths[i], MAX_COLUMN_WIDTH_CHARS) * 256);
            }
            sheet.createFreezePane(0, headerRowIndex + 1);
            if (lastDataRow > headerRowIndex) {
                sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, lastDataRow, 0, columns.size() - 1));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Erreur lors de la génération du fichier Excel", e);
        }
    }

    private void writeValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number && !(value instanceof BigDecimal)) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else if (value instanceof Instant instant) {
            cell.setCellValue(instant.atZone(zoneId).toLocalDateTime());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    private static int displayLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Instant) {
            return 16;
        }
        return String.valueOf(value).length();
    }

    private static String safeSheetName(String title) {
        String cleaned = title.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** Styles partagés (POI limite le nombre de styles par classeur : ils sont créés une seule fois). */
    private static final class Styles {

        final CellStyle title;
        final CellStyle subtitle;
        final CellStyle header;
        final CellStyle summaryTitle;
        private final Map<TableReport.Highlight, Map<TableReport.ColumnType, CellStyle>> body =
                new EnumMap<>(TableReport.Highlight.class);

        Styles(Workbook wb, int decimals) {
            CreationHelper helper = wb.getCreationHelper();

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            title = wb.createCellStyle();
            title.setFont(titleFont);

            Font italic = wb.createFont();
            italic.setItalic(true);
            italic.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            subtitle = wb.createCellStyle();
            subtitle.setFont(italic);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            borders(header);

            Font bold = wb.createFont();
            bold.setBold(true);
            summaryTitle = wb.createCellStyle();
            summaryTitle.setFont(bold);

            for (TableReport.Highlight highlight : TableReport.Highlight.values()) {
                Map<TableReport.ColumnType, CellStyle> byType = new EnumMap<>(TableReport.ColumnType.class);
                for (TableReport.ColumnType type : TableReport.ColumnType.values()) {
                    CellStyle style = wb.createCellStyle();
                    borders(style);
                    switch (type) {
                        case INTEGER -> {
                            style.setDataFormat(helper.createDataFormat().getFormat("#,##0"));
                            style.setAlignment(HorizontalAlignment.RIGHT);
                        }
                        case DECIMAL -> {
                            style.setDataFormat(helper.createDataFormat().getFormat(
                                    decimals > 0 ? "#,##0." + "0".repeat(decimals) : "#,##0"));
                            style.setAlignment(HorizontalAlignment.RIGHT);
                        }
                        case DATETIME -> {
                            style.setDataFormat(helper.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
                            style.setAlignment(HorizontalAlignment.CENTER);
                        }
                        case TEXT -> style.setAlignment(HorizontalAlignment.LEFT);
                    }
                    IndexedColors fill = switch (highlight) {
                        case DANGER -> IndexedColors.ROSE;
                        case WARNING -> IndexedColors.LIGHT_YELLOW;
                        case SUCCESS -> IndexedColors.LIGHT_GREEN;
                        case NONE -> null;
                    };
                    if (fill != null) {
                        style.setFillForegroundColor(fill.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    }
                    byType.put(type, style);
                }
                body.put(highlight, byType);
            }
        }

        CellStyle body(TableReport.ColumnType type, TableReport.Highlight highlight) {
            return body.get(highlight).get(type);
        }

        private static void borders(CellStyle style) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
