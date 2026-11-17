package ma.pfe.stock.reporting.domain.model;

import java.time.Instant;

public record AlertData(Long id, String productReference, String productName, String type, String typeLabel,
                        String status, int quantity, int threshold, Instant createdAt) {
}
