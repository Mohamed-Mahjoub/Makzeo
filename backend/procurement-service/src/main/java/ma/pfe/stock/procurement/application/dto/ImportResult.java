package ma.pfe.stock.procurement.application.dto;

import java.util.List;

/** Résultat d'un import de l'historique d'achats. */
public record ImportResult(int imported, List<String> rejected) {
}
