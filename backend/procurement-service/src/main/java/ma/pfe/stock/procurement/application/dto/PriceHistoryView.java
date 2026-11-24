package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PricePrediction;

import java.util.List;

public record PriceHistoryView(Long productId, List<PricePoint> points, PricePrediction prediction) {
}
