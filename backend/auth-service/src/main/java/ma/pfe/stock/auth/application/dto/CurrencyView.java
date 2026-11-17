package ma.pfe.stock.auth.application.dto;

/**
 * Devise proposée dans la liste de choix.
 *
 * @param common true pour les devises les plus courantes (affichées en premier)
 */
public record CurrencyView(String code, String name, String symbol, int fractionDigits, boolean common) {
}
