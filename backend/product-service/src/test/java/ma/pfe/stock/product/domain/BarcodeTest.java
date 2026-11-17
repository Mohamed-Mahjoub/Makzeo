package ma.pfe.stock.product.domain;

import ma.pfe.stock.product.domain.model.Barcode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"4006381333931", "6111242100992", "5901234123457"})
    void acceptsValidEan13(String code) {
        assertThat(new Barcode(code).isEan13()).isTrue();
    }

    @Test
    void rejectsEan13WithWrongCheckDigit() {
        assertThatThrownBy(() -> new Barcode("4006381333932"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clé de contrôle attendue : 1");
    }

    @Test
    void acceptsInternalCode128AndNormalizesCase() {
        Barcode barcode = new Barcode(" int-abc-001 ");
        assertThat(barcode.value()).isEqualTo("INT-ABC-001");
        assertThat(barcode.isEan13()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "AB", "code avec espaces", "É1234"})
    void rejectsInvalidCodes(String code) {
        assertThatThrownBy(() -> new Barcode(code)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesInternalEan13WithValidCheckDigit() {
        Barcode generated = Barcode.internalEan13(42);
        assertThat(generated.value()).hasSize(13).startsWith("200000000042");
        assertThat(new Barcode(generated.value())).isEqualTo(generated);
    }
}
