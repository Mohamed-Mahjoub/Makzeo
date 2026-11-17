package ma.pfe.stock.supplier.domain;

import ma.pfe.stock.supplier.domain.model.Countries;
import ma.pfe.stock.supplier.domain.model.Supplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierTest {

    @Test
    void normalizesFields() {
        Supplier s = Supplier.create(" f001 ", " Atlas Distribution ", "", "Contact@Atlas.MA", null, null,
                "Rabat", "ma", "001234567000089", null);

        assertThat(s.getCode()).isEqualTo("F001");
        assertThat(s.getName()).isEqualTo("Atlas Distribution");
        assertThat(s.getContactName()).isNull();
        assertThat(s.getEmail()).isEqualTo("contact@atlas.ma");
        assertThat(s.getCountry()).isEqualTo("MA");
        assertThat(s.getCountryName()).isEqualTo("Maroc");
        assertThat(s.isActive()).isTrue();
    }

    @Test
    void acceptsSuppliersFromAnyCountryWithTheirTaxIdentifier() {
        Supplier tunisian = Supplier.create("T1", "Carthage Fournitures", null, null, null, null, "Tunis", "TN",
                "1234567/A/M/000", null);
        Supplier french = Supplier.create("F2", "Lyon Bureau", null, null, null, null, "Lyon", "FR",
                "FR40303265045", null);
        Supplier noCountry = Supplier.create("X1", "Sans pays", null, null, null, null, null, null, null, null);

        assertThat(tunisian.getCountryName()).isEqualTo("Tunisie");
        assertThat(tunisian.getTaxId()).isEqualTo("1234567/A/M/000");
        assertThat(french.getCountryName()).isEqualTo("France");
        assertThat(noCountry.getCountry()).isNull();
    }

    @Test
    void moroccanTaxIdMustBeAFifteenDigitIce() {
        assertThatThrownBy(() -> Supplier.create("F1", "X", null, null, null, null, null, "MA", "12AB", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15 chiffres");
    }

    @Test
    void rejectsUnknownCountryCode() {
        assertThatThrownBy(() -> Supplier.create("F1", "X", null, null, null, null, null, "ZZ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO 3166");
    }

    @Test
    void countryListContainsAllCountriesSortedInFrench() {
        assertThat(Countries.all()).hasSizeGreaterThan(240);
        assertThat(Countries.all().getFirst().name()).isEqualTo("Afghanistan");
        assertThat(Countries.all()).extracting(Countries.Country::code).contains("MA", "TN", "DZ", "FR", "US", "SN");
    }
}
