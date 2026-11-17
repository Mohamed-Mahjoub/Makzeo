package ma.pfe.stock.auth.application;

import ma.pfe.stock.auth.application.dto.CreateUserCommand;
import ma.pfe.stock.auth.application.dto.UpdateUserCommand;
import ma.pfe.stock.auth.application.port.PasswordHasher;
import ma.pfe.stock.auth.application.service.UserService;
import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.domain.port.UserRepository;
import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository repository;
    private UserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        PasswordHasher hasher = mock(PasswordHasher.class);
        when(hasher.hash(any())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new UserService(repository, hasher);
    }

    @Test
    void createsUserWithHashedPassword() {
        var view = service.create(new CreateUserCommand("Mag1", "mag1@x.ma", "Magasinier", "Password1",
                Set.of(Role.MAGASINIER)));

        assertThat(view.username()).isEqualTo("mag1");
        assertThat(view.roles()).containsExactly(Role.MAGASINIER);
    }

    @Test
    void rejectsDuplicateUsername() {
        when(repository.existsByUsername("mag1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserCommand("mag1", "m@x.ma", "M", "Password1",
                Set.of(Role.MAGASINIER)))).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsWeakPassword() {
        assertThatThrownBy(() -> service.create(new CreateUserCommand("mag2", "m2@x.ma", "M", "short",
                Set.of(Role.MAGASINIER)))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        User admin = new User(1L, "admin", "a@x.ma", "Admin", "h", Set.of(Role.ADMIN), true, null, null);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.update(1L, new UpdateUserCommand("a@x.ma", "Admin",
                Set.of(Role.CONSULTANT)), "admin"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminCannotDisableOwnAccount() {
        User admin = new User(1L, "admin", "a@x.ma", "Admin", "h", Set.of(Role.ADMIN), true, null, null);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.setEnabled(1L, false, "admin")).isInstanceOf(BusinessRuleException.class);
    }
}
