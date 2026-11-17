package ma.pfe.stock.reporting.application.port;

import ma.pfe.stock.reporting.domain.model.ReportFormat;
import ma.pfe.stock.reporting.domain.model.TableReport;

/** Port : rendu d'un rapport tabulaire dans un format donné (un adaptateur par format). */
public interface ReportRenderer {

    ReportFormat format();

    byte[] render(TableReport report);
}
