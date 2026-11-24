package ma.pfe.stock.procurement.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.procurement.application.dto.QuoteCommand;
import ma.pfe.stock.procurement.application.dto.SupplierCommand;
import ma.pfe.stock.procurement.domain.model.Availability;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.QuoteSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Corps des requêtes de l'API achats. */
public final class ProcurementRequests {

    private ProcurementRequests() {
    }

    public record SupplierRequest(@NotBlank @Size(max = 150) String name, @Size(max = 255) String website,
                                  @Email @Size(max = 150) String email, @Size(max = 40) String phone,
                                  @Size(max = 80) String city, @Size(max = 1000) String notes) {
        SupplierCommand toCommand() {
            return new SupplierCommand(name, website, email, phone, city, notes);
        }
    }

    public record RfqDraftRequest(@NotNull Long productId, @NotNull Long marketSupplierId,
                                  @NotNull @Min(1) @Max(1_000_000) Integer quantity) {
    }

    public record RfqUpdateRequest(@NotNull @Min(1) @Max(1_000_000) Integer quantity,
                                   @NotBlank @Size(max = 200) String subject,
                                   @NotBlank @Size(max = 7000) String body, LocalDate responseDueDate) {
    }

    public record IdsRequest(@NotEmpty @Size(max = 100) List<Long> ids) {
    }

    public record QuoteRequest(Long rfqId, @NotNull Long productId, @NotNull Long marketSupplierId,
                               @NotBlank @Size(max = 255) String offeredName, Equivalence equivalence,
                               @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3)
                               BigDecimal unitPrice, boolean priceIncludesVat, @Min(1) Integer minQuantity,
                               Availability availability, @Min(0) @Max(365) Integer leadTimeDays,
                               @Size(max = 255) String paymentTerms, LocalDate validUntil,
                               @Min(1) @Max(5) Integer qualityScore, QuoteSource source) {
        QuoteCommand toCommand() {
            QuoteSource effective = source == QuoteSource.AI_EXTRACTED ? QuoteSource.AI_EXTRACTED
                    : rfqId != null ? QuoteSource.RFQ_REPLY : QuoteSource.MANUAL;
            return new QuoteCommand(rfqId, productId, marketSupplierId, offeredName, equivalence, unitPrice,
                    priceIncludesVat, minQuantity, availability, leadTimeDays, paymentTerms, validUntil, qualityScore,
                    effective);
        }
    }

    public record ExtractRequest(@NotNull Long productId, @NotBlank @Size(max = 20000) String text) {
    }

    public record PurchaseRequest(@NotNull Long productId,
                                  @NotNull @DecimalMin(value = "0.001") @Digits(integer = 11, fraction = 3)
                                  BigDecimal unitPrice,
                                  @NotNull LocalDate date, @Size(max = 150) String supplierName,
                                  @Min(1) Integer quantity) {
    }

    public record ImportRequest(@NotBlank @Size(max = 2_000_000) String csv) {
    }
}
