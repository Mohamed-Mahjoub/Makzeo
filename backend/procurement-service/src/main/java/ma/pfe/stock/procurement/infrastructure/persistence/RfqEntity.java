package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rfqs")
public class RfqEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String reference;
    @Column(nullable = false)
    private Long productId;
    @Column(nullable = false, length = 50)
    private String productReference;
    @Column(nullable = false, length = 150)
    private String productName;
    @Column(nullable = false)
    private Long marketSupplierId;
    @Column(nullable = false, length = 150)
    private String supplierName;
    @Column(length = 150)
    private String supplierEmail;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, length = 255)
    private String subject;
    @Column(nullable = false, length = 8000)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RfqStatus status;
    private LocalDate responseDueDate;
    @Column(nullable = false, length = 50)
    private String createdBy;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(length = 50)
    private String approvedBy;
    private Instant approvedAt;
    private Instant sentAt;
    @Column(length = 500)
    private String failureReason;

    protected RfqEntity() {
    }

    static RfqEntity from(Rfq r) {
        RfqEntity e = new RfqEntity();
        e.id = r.getId();
        e.reference = r.getReference();
        e.productId = r.getProductId();
        e.productReference = r.getProductReference();
        e.productName = SourcingLeadEntity.cut(r.getProductName(), 150);
        e.marketSupplierId = r.getMarketSupplierId();
        e.supplierName = SourcingLeadEntity.cut(r.getSupplierName(), 150);
        e.supplierEmail = r.getSupplierEmail();
        e.quantity = r.getQuantity();
        e.subject = SourcingLeadEntity.cut(r.getSubject(), 255);
        e.body = SourcingLeadEntity.cut(r.getBody(), 8000);
        e.status = r.getStatus();
        e.responseDueDate = r.getResponseDueDate();
        e.createdBy = r.getCreatedBy();
        e.createdAt = r.getCreatedAt();
        e.approvedBy = r.getApprovedBy();
        e.approvedAt = r.getApprovedAt();
        e.sentAt = r.getSentAt();
        e.failureReason = r.getFailureReason();
        return e;
    }

    Rfq toDomain() {
        return new Rfq(id, reference, productId, productReference, productName, marketSupplierId, supplierName,
                supplierEmail, quantity, subject, body, status, responseDueDate, createdBy, createdAt, approvedBy,
                approvedAt, sentAt, failureReason);
    }
}
