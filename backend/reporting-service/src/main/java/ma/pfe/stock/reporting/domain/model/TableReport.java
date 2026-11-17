package ma.pfe.stock.reporting.domain.model;

import java.util.List;

/**
 * Modèle de rapport tabulaire indépendant du format de sortie (PDF / Excel).
 * La couche application construit ce modèle, la couche infrastructure le "rend".
 */
public record TableReport(
        String title,
        List<String> subtitles,
        List<Column> columns,
        List<Row> rows,
        List<String> summary,
        int decimals) {

    /** Rapport dont les montants ont 2 décimales. */
    public TableReport(String title, List<String> subtitles, List<Column> columns, List<Row> rows, List<String> summary) {
        this(title, subtitles, columns, rows, summary, 2);
    }

    public enum Align { LEFT, CENTER, RIGHT }

    public enum ColumnType { TEXT, INTEGER, DECIMAL, DATETIME }

    /** Mise en évidence d'une ligne (ex. rupture en rouge). */
    public enum Highlight { NONE, WARNING, DANGER, SUCCESS }

    /** @param weight largeur relative de la colonne (PDF) */
    public record Column(String header, ColumnType type, float weight) {

        public Align align() {
            return switch (type) {
                case INTEGER, DECIMAL -> Align.RIGHT;
                case DATETIME -> Align.CENTER;
                case TEXT -> Align.LEFT;
            };
        }
    }

    public record Row(List<Object> cells, Highlight highlight) {

        public Row(List<Object> cells) {
            this(cells, Highlight.NONE);
        }
    }
}
