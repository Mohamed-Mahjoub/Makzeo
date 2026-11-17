package ma.pfe.stock.reporting.domain.model;

/** Fichier de rapport produit. */
public record GeneratedReport(String fileName, String contentType, byte[] content) {
}
