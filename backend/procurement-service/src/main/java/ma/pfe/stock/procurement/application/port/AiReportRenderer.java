package ma.pfe.stock.procurement.application.port;

import ma.pfe.stock.procurement.application.dto.AiReport;

/** Rendu du rapport de l'agent IA dans un format imprimable. */
public interface AiReportRenderer {

    byte[] render(AiReport report);
}
