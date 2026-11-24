package ma.pfe.stock.procurement.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Demande de prix (Request For Quotation) adressée à un fournisseur pour un article. */
public class Rfq {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Long id;
    private final String reference;
    private final Long productId;
    private final String productReference;
    private final String productName;
    private final Long marketSupplierId;
    private final String supplierName;
    private String supplierEmail;
    private int quantity;
    private String subject;
    private String body;
    private RfqStatus status;
    private LocalDate responseDueDate;
    private final String createdBy;
    private final Instant createdAt;
    private String approvedBy;
    private Instant approvedAt;
    private Instant sentAt;
    private String failureReason;

    public Rfq(Long id, String reference, Long productId, String productReference, String productName,
               Long marketSupplierId, String supplierName, String supplierEmail, int quantity, String subject,
               String body, RfqStatus status, LocalDate responseDueDate, String createdBy, Instant createdAt,
               String approvedBy, Instant approvedAt, Instant sentAt, String failureReason) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité demandée doit être positive");
        }
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("L'objet et le message de la demande de prix sont obligatoires");
        }
        this.id = id;
        this.reference = reference;
        this.productId = productId;
        this.productReference = productReference;
        this.productName = productName;
        this.marketSupplierId = marketSupplierId;
        this.supplierName = supplierName;
        this.supplierEmail = supplierEmail;
        this.quantity = quantity;
        this.subject = subject.trim();
        this.body = body.trim();
        this.status = status;
        this.responseDueDate = responseDueDate;
        this.createdBy = createdBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.approvedBy = approvedBy;
        this.approvedAt = approvedAt;
        this.sentAt = sentAt;
        this.failureReason = failureReason;
    }

    public static Rfq draft(Long productId, String productReference, String productName, MarketSupplier supplier,
                            int quantity, String subject, String body, LocalDate responseDueDate, String createdBy) {
        String reference = newReference(YearMonth.now());
        String fullSubject = subject.contains(reference) ? subject : "[" + reference + "] " + subject;
        return new Rfq(null, reference, productId, productReference, productName, supplier.getId(),
                supplier.getName(), supplier.getEmail(), quantity, fullSubject, body, RfqStatus.DRAFT,
                responseDueDate, createdBy, Instant.now(), null, null, null, null);
    }

    /** Référence lisible et unique, rappelée dans l'objet du courriel : RFQ-202611-K7Q2MX. */
    public static String newReference(YearMonth month) {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return "RFQ-" + month.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + code;
    }

    public void editDraft(int quantity, String subject, String body, LocalDate responseDueDate) {
        if (status != RfqStatus.DRAFT) {
            throw new BusinessRuleException("RFQ_NOT_DRAFT", "Seule une demande de prix en brouillon est modifiable");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité demandée doit être positive");
        }
        if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("L'objet et le message de la demande de prix sont obligatoires");
        }
        this.quantity = quantity;
        this.subject = subject.contains(reference) ? subject.trim() : "[" + reference + "] " + subject.trim();
        this.body = body.trim();
        this.responseDueDate = responseDueDate;
    }

    /** Validation par le responsable des achats ; possible aussi après un échec d'envoi. */
    public void approve(String user, String currentSupplierEmail) {
        if (status != RfqStatus.DRAFT && status != RfqStatus.FAILED) {
            throw new BusinessRuleException("RFQ_NOT_APPROVABLE",
                    "La demande de prix " + reference + " ne peut plus être validée (statut " + status + ")");
        }
        if (currentSupplierEmail == null || currentSupplierEmail.isBlank()) {
            throw new BusinessRuleException("RFQ_NO_EMAIL",
                    "Le fournisseur " + supplierName + " n'a pas d'adresse e-mail : complétez sa fiche");
        }
        this.supplierEmail = currentSupplierEmail;
        this.status = RfqStatus.APPROVED;
        this.approvedBy = user;
        this.approvedAt = Instant.now();
        this.failureReason = null;
    }

    public void markSent() {
        if (status != RfqStatus.APPROVED) {
            throw new BusinessRuleException("RFQ_NOT_APPROVED", "Une demande de prix doit être validée avant envoi");
        }
        this.status = RfqStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = RfqStatus.FAILED;
        this.failureReason = reason == null ? "Erreur inconnue"
                : reason.length() > 500 ? reason.substring(0, 500) : reason;
    }

    /** Un devis a été reçu : la demande est close. */
    public void markAnswered() {
        if (status == RfqStatus.SENT || status == RfqStatus.APPROVED) {
            this.status = RfqStatus.ANSWERED;
        }
    }

    public void cancel() {
        if (status == RfqStatus.SENT || status == RfqStatus.ANSWERED) {
            throw new BusinessRuleException("RFQ_ALREADY_SENT", "Une demande de prix déjà envoyée ne peut être annulée");
        }
        this.status = RfqStatus.CANCELLED;
    }

    public boolean isOpen() {
        return status == RfqStatus.DRAFT || status == RfqStatus.APPROVED || status == RfqStatus.SENT
                || status == RfqStatus.FAILED;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Long getProductId() { return productId; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public Long getMarketSupplierId() { return marketSupplierId; }
    public String getSupplierName() { return supplierName; }
    public String getSupplierEmail() { return supplierEmail; }
    public int getQuantity() { return quantity; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public RfqStatus getStatus() { return status; }
    public LocalDate getResponseDueDate() { return responseDueDate; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getSentAt() { return sentAt; }
    public String getFailureReason() { return failureReason; }
}
