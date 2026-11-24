package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.PricePoint;
import ma.pfe.stock.procurement.domain.model.PriceSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "price_points")
public class PricePointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private LocalDate observedOn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceSource source;
    @Column(length = 150)
    private String supplierName;
    private Integer quantity;
    @Column(nullable = false)
    private Instant createdAt;

    protected PricePointEntity() {
    }

    static PricePointEntity from(PricePoint p) {
        PricePointEntity e = new PricePointEntity();
        e.id = p.id();
        e.productId = p.productId();
        e.unitPrice = p.unitPrice();
        e.observedOn = p.observedOn();
        e.source = p.source();
        e.supplierName = SourcingLeadEntity.cut(p.supplierName(), 150);
        e.quantity = p.quantity();
        e.createdAt = Instant.now();
        return e;
    }

    PricePoint toDomain() {
        return new PricePoint(id, productId, unitPrice, observedOn, source, supplierName, quantity);
    }
}
