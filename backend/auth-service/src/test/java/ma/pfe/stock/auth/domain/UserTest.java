package ma.pfe.stock.auth.domain;

import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void normalizesUsernameAndEmail() {
        User user = User.create("  JDoe ", "John.Doe@Mail.COM", "John Doe", "hash", Set.of(Role.MAGASINIER));

        assertThat(user.getUsername()).isEqualTo("jdoe");
        assertThat(user.getEmail()).isEqualTo("john.doe@mail.com");
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void requiresAtLeastOneRole() {
        assertThatThrownBy(() -> User.create("jdoe", "j@d.com", "John", "hash", Set.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void canBeDisabledAndEnabled() {
        User user = User.create("jdoe", "j@d.com", "John", "hash", Set.of(Role.CONSULTANT));
        user.disable();
        assertThat(user.isEnabled()).isFalse();
        user.enable();
        assertThat(user.isEnabled()).isTrue();
    }
}
