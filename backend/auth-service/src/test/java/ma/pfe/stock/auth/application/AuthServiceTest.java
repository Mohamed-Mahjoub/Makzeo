package ma.pfe.stock.auth.application;

import ma.pfe.stock.auth.application.dto.AuthResult;
import ma.pfe.stock.auth.application.port.PasswordHasher;
import ma.pfe.stock.auth.application.port.TokenProvider;
import ma.pfe.stock.auth.application.service.AuthService;
import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.domain.port.UserRepository;
import ma.pfe.stock.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository repository;
    private PasswordHasher hasher;
    private TokenProvider tokenProvider;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        hasher = mock(PasswordHasher.class);
        tokenProvider = mock(TokenProvider.class);
        service = new AuthService(repository, hasher, tokenProvider);
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreValid() {
        User user = new User(1L, "admin", "admin@x.ma", "Admin", "hash", Set.of(Role.ADMIN), true, null, null);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(hasher.matches("secret123", "hash")).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenProvider.issue(any())).thenReturn(new TokenProvider.IssuedToken("jwt", Instant.now()));

        AuthResult result = service.login(" ADMIN ", "secret123");

        assertThat(result.accessToken()).isEqualTo("jwt");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.user().lastLoginAt()).isNotNull();
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User(1L, "admin", "admin@x.ma", "Admin", "hash", Set.of(Role.ADMIN), true, null, null);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(hasher.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin", "bad")).isInstanceOf(AuthenticationFailedException.class);
        verify(tokenProvider, never()).issue(any());
    }

    @Test
    void loginFailsWhenAccountDisabled() {
        User user = new User(1L, "bob", "bob@x.ma", "Bob", "hash", Set.of(Role.MAGASINIER), false, null, null);
        when(repository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(hasher.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login("bob", "secret123"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("désactivé");
    }

    @Test
    void loginFailsForUnknownUser() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "x")).isInstanceOf(AuthenticationFailedException.class);
    }
}
