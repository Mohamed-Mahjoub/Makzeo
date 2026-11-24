package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.Quote;
import ma.pfe.stock.procurement.domain.model.QuoteSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "quotes")
public class QuoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long rfqId;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false)
    private Long marketSupplierId;
    @Column(nullable = false, length = 150)
    private String supplierName;
    @Column(nullable = false, length = 255)
    private String offeredName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Equivalence equivalence;
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal unitPrice;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private int minQuantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Availability availability;
    private Integer leadTimeDays;
    @Column(length = 255)
    private String paymentTerms;
    private LocalDate validUntil;
    @Column(nullable = false)
    private int qualityScore;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteSource source;
    @Column(nullable = false)
    private Instant receivedAt;

    protected QuoteEntity() {
    }

    static QuoteEntity from(Quote q) {
        QuoteEntity e = new QuoteEntity();
        e.id = q.id();
        e.rfqId = q.rfqId();
        e.productId = q.productId();
        e.marketSupplierId = q.marketSupplierId();
        e.supplierName = SourcingLeadEntity.cut(q.supplierName(), 150);
        e.offeredName = SourcingLeadEntity.cut(q.offeredName(), 255);
        e.equivalence = q.equivalence();
        e.unitPrice = q.unitPrice();
        e.currency = q.currency();
        e.minQuantity = q.minQuantity();
        e.availability = q.availability();
        e.leadTimeDays = q.leadTimeDays();
        e.paymentTerms = SourcingLeadEntity.cut(q.paymentTerms(), 255);
        e.validUntil = q.validUntil();
        e.qualityScore = q.qualityScore();
        e.source = q.source();
        e.receivedAt = q.receivedAt();
        return e;
    }

    Quote toDomain() {
        return new Quote(id, rfqId, productId, marketSupplierId, supplierName, offeredName, equivalence, unitPrice,
                currency, minQuantity, availability, leadTimeDays, paymentTerms, validUntil, qualityScore, source,
                receivedAt);
    }
}
