package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.SourcingLead;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sourcing_leads")
public class SourcingLeadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false)
    private Long marketSupplierId;
    @Column(nullable = false, length = 255)
    private String offeredName;
    @Column(length = 100)
    private String offeredReference;
    @Column(length = 500)
    private String url;
    @Column(precision = 14, scale = 3)
    private BigDecimal publishedPrice;
    @Column(length = 3)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Equivalence equivalence;
    @Column(nullable = false)
    private int similarity;
    @Column(length = 1000)
    private String rationale;
    private Long watchRunId;
    @Column(nullable = false)
    private Instant detectedAt;

    protected SourcingLeadEntity() {
    }

    static SourcingLeadEntity from(SourcingLead l) {
        SourcingLeadEntity e = new SourcingLeadEntity();
        e.id = l.id();
        e.productId = l.productId();
        e.marketSupplierId = l.marketSupplierId();
        e.offeredName = cut(l.offeredName(), 255);
        e.offeredReference = cut(l.offeredReference(), 100);
        e.url = cut(l.url(), 500);
        e.publishedPrice = l.publishedPrice();
        e.currency = l.currency();
        e.equivalence = l.equivalence();
        e.similarity = l.similarity();
        e.rationale = cut(l.rationale(), 1000);
        e.watchRunId = l.watchRunId();
        e.detectedAt = l.detectedAt();
        return e;
    }

    SourcingLead toDomain(String supplierName) {
        return new SourcingLead(id, productId, marketSupplierId, supplierName, offeredName, offeredReference, url,
                publishedPrice, currency, equivalence, similarity, rationale, watchRunId, detectedAt);
    }

    Long getMarketSupplierId() {
        return marketSupplierId;
    }

    static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
