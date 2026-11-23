package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.SupplierSource;

import java.time.Instant;

public record MarketSupplierView(Long id, String name, String website, String email, String phone, String city,
                                 SupplierSource source, MarketSupplierStatus status, Long catalogSupplierId,
                                 String notes, Instant discoveredAt, boolean canReceiveRfq) {

    public static MarketSupplierView from(MarketSupplier s) {
        return new MarketSupplierView(s.getId(), s.getName(), s.getWebsite(), s.getEmail(), s.getPhone(), s.getCity(),
                s.getSource(), s.getStatus(), s.getCatalogSupplierId(), s.getNotes(), s.getDiscoveredAt(),
                s.canReceiveRfq());
    }
}
