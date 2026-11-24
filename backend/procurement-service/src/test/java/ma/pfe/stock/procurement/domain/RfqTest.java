package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;
import ma.pfe.stock.procurement.domain.model.SupplierSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RfqTest {

    private static final MarketSupplier SUPPLIER = new MarketSupplier(7L, "Fournisseur A", null, "ventes@a.tn", null,
            "Tunis", SupplierSource.MANUAL, MarketSupplierStatus.VALIDATED, null, null, Instant.now(), null);

    private static Rfq draft() {
        return Rfq.draft(1L, "PRD-1", "Clavier USB", SUPPLIER, 20, "Demande de prix", "Bonjour", LocalDate.now(),
                "acheteur");
    }

    @Test
    void draftCarriesAUniqueReferenceInItsSubject() {
        Rfq rfq = draft();
        assertThat(rfq.getReference()).matches("RFQ-\\d{6}-[A-Z2-9]{6}");
        assertThat(rfq.getSubject()).startsWith("[" + rfq.getReference() + "]");
        assertThat(rfq.getStatus()).isEqualTo(RfqStatus.DRAFT);
    }

    @Test
    void followsTheValidationThenSendingWorkflow() {
        Rfq rfq = draft();
        rfq.editDraft(30, "Nouvel objet", "Nouveau texte", null);
        assertThat(rfq.getQuantity()).isEqualTo(30);
        assertThat(rfq.getSubject()).contains(rfq.getReference());

        rfq.approve("responsable", "ventes@a.tn");
        assertThat(rfq.getStatus()).isEqualTo(RfqStatus.APPROVED);
        assertThatThrownBy(() -> rfq.editDraft(5, "x", "y", null)).isInstanceOf(BusinessRuleException.class);

        rfq.markSent();
        assertThat(rfq.getStatus()).isEqualTo(RfqStatus.SENT);
        assertThat(rfq.getSentAt()).isNotNull();
        assertThatThrownBy(rfq::cancel).isInstanceOf(BusinessRuleException.class);

        rfq.markAnswered();
        assertThat(rfq.getStatus()).isEqualTo(RfqStatus.ANSWERED);
    }

    @Test
    void cannotBeApprovedWithoutEmailAndCanBeRetriedAfterFailure() {
        Rfq rfq = draft();
        assertThatThrownBy(() -> rfq.approve("responsable", null)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("adresse e-mail");
        rfq.approve("responsable", "ventes@a.tn");
        rfq.markFailed("SMTP indisponible");
        assertThat(rfq.getStatus()).isEqualTo(RfqStatus.FAILED);
        rfq.approve("responsable", "ventes@a.tn");
        assertThat(rfq.getFailureReason()).isNull();
    }

    @Test
    void cannotBeSentBeforeApproval() {
        assertThatThrownBy(draft()::markSent).isInstanceOf(BusinessRuleException.class);
    }
}
