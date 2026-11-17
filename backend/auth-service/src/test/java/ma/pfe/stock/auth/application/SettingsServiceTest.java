package ma.pfe.stock.auth.application;

import ma.pfe.stock.auth.application.dto.CurrencyView;
import ma.pfe.stock.auth.application.dto.SettingsView;
import ma.pfe.stock.auth.application.service.SettingsService;
import ma.pfe.stock.auth.domain.model.AppSettings;
import ma.pfe.stock.auth.domain.port.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsServiceTest {

    private SettingsService service;

    @BeforeEach
    void setUp() {
        SettingsRepository repository = new SettingsRepository() {
            private AppSettings stored;

            public Optional<AppSettings> load() { return Optional.ofNullable(stored); }

            public AppSettings save(AppSettings settings) { stored = settings; return settings; }
        };
        service = new SettingsService(repository);
    }

    @Test
    void defaultsToMoroccanDirham() {
        assertThat(service.get().currency()).isEqualTo("MAD");
    }

    @Test
    void changesCurrencyAndExposesItsFractionDigits() {
        SettingsView view = service.changeCurrency("tnd", "admin");

        assertThat(view.currency()).isEqualTo("TND");
        assertThat(view.currencyFractionDigits()).isEqualTo(3);
        assertThat(view.currencyName()).containsIgnoringCase("dinar tunisien");
        assertThat(view.updatedBy()).isEqualTo("admin");
        assertThat(service.get().currency()).isEqualTo("TND");
    }

    @Test
    void rejectsUnknownCurrency() {
        assertThatThrownBy(() -> service.changeCurrency("ABC", "admin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsCommonCurrenciesFirstThenAllOthers() {
        List<CurrencyView> list = service.availableCurrencies();

        assertThat(list).hasSizeGreaterThan(140);
        assertThat(list.subList(0, 5)).extracting(CurrencyView::code).containsExactly("MAD", "TND", "DZD", "EUR", "USD");
        assertThat(list).extracting(CurrencyView::code).contains("JPY", "BRL", "INR").doesNotHaveDuplicates();
        assertThat(list).filteredOn(CurrencyView::common).hasSize(18);
    }
}
