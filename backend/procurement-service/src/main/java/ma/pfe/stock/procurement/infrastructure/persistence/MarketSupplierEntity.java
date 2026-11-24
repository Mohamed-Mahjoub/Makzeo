package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.SupplierSource;

import java.time.Instant;

@Entity
@Table(name = "market_suppliers")
public class MarketSupplierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, unique = true, length = 150)
    private String normalizedKey;
    @Column(length = 255)
    private String website;
    @Column(length = 150)
    private String email;
    @Column(length = 40)
    private String phone;
    @Column(length = 80)
    private String city;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarketSupplierStatus status;
    private Long catalogSupplierId;
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false)
    private Instant discoveredAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected MarketSupplierEntity() {
    }

    static MarketSupplierEntity from(MarketSupplier s) {
        MarketSupplierEntity e = new MarketSupplierEntity();
        e.id = s.getId();
        e.name = s.getName();
        e.normalizedKey = s.getNormalizedKey();
        e.website = s.getWebsite();
        e.email = s.getEmail();
        e.phone = s.getPhone();
        e.city = s.getCity();
        e.source = s.getSource();
        e.status = s.getStatus();
        e.catalogSupplierId = s.getCatalogSupplierId();
        e.notes = s.getNotes();
        e.discoveredAt = s.getDiscoveredAt();
        e.updatedAt = s.getUpdatedAt();
        return e;
    }

    MarketSupplier toDomain() {
        return new MarketSupplier(id, name, website, email, phone, city, source, status, catalogSupplierId, notes,
                discoveredAt, updatedAt);
    }
}
