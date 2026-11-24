package ma.pfe.stock.procurement.domain;

import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.procurement.domain.model.MarketSupplier;
import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketSupplierTest {

    @Test
    void normalizedKeyIgnoresCaseAccentsAndLegalForms() {
        assertThat(MarketSupplier.normalizeKey("Sté MYTEK SARL")).isEqualTo("mytek");
        assertThat(MarketSupplier.normalizeKey("mytek")).isEqualTo("mytek");
        assertThat(MarketSupplier.normalizeKey("Électro-Ménager Tunisie S.A."))
                .isEqualTo(MarketSupplier.normalizeKey("electro menager"));
    }

    @Test
    void discoveredSupplierMustBeValidatedBeforeReceivingRfqs() {
        MarketSupplier s = MarketSupplier.discovered("Fournisseur web", "fournisseur.tn", "Contact@Fournisseur.TN",
                null, "Sfax");
        assertThat(s.getStatus()).isEqualTo(MarketSupplierStatus.CANDIDATE);
        assertThat(s.getWebsite()).isEqualTo("https://fournisseur.tn");
        assertThat(s.getEmail()).isEqualTo("contact@fournisseur.tn");
        assertThat(s.canReceiveRfq()).isFalse();
        s.validate();
        assertThat(s.canReceiveRfq()).isTrue();
    }

    @Test
    void invalidEmailsAreDiscardedAndKnownContactsAreKept() {
        MarketSupplier s = MarketSupplier.discovered("Fournisseur", null, "pas-une-adresse", null, null);
        assertThat(s.getEmail()).isNull();
        s.completeContact(null, "achats@f.tn", "71 000 000", "Tunis");
        s.completeContact(null, "autre@f.tn", null, "Sousse");
        assertThat(s.getEmail()).isEqualTo("achats@f.tn");
        assertThat(s.getCity()).isEqualTo("Tunis");
    }

    @Test
    void catalogSuppliersCannotBeRejectedHere() {
        MarketSupplier s = MarketSupplier.fromCatalog(3L, "Fournisseur catalogue", "a@b.tn", null, null);
        assertThat(s.getStatus()).isEqualTo(MarketSupplierStatus.VALIDATED);
        assertThatThrownBy(s::reject).isInstanceOf(BusinessRuleException.class);
    }
}
