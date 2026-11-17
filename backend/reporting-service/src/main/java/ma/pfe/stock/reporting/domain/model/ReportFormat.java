package ma.pfe.stock.reporting.domain.model;

/** Formats de sortie disponibles. */
public enum ReportFormat {
    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    ReportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static ReportFormat parse(String value) {
        if (value == null || value.isBlank()) {
            return PDF;
        }
        return switch (value.trim().toLowerCase()) {
            case "pdf" -> PDF;
            case "xlsx", "excel", "xls" -> XLSX;
            default -> throw new IllegalArgumentException("Format non supporté : " + value + " (pdf ou xlsx)");
        };
    }
}
