package ma.pfe.stock.auth.application.service;

import ma.pfe.stock.auth.application.dto.AuthResult;
import ma.pfe.stock.auth.application.dto.UserView;
import ma.pfe.stock.auth.application.port.PasswordHasher;
import ma.pfe.stock.auth.application.port.TokenProvider;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.domain.port.UserRepository;
import ma.pfe.stock.common.exception.AuthenticationFailedException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cas d'utilisation : authentification. */
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Nom d'utilisateur ou mot de passe incorrect";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, TokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResult login(String username, String password) {
        User user = userRepository.findByUsername(username == null ? "" : username.trim().toLowerCase())
                .orElseThrow(() -> new AuthenticationFailedException(INVALID_CREDENTIALS));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException("Compte désactivé, contactez l'administrateur");
        }
        user.recordLogin();
        User saved = userRepository.save(user);
        TokenProvider.IssuedToken token = tokenProvider.issue(saved);
        return new AuthResult(token.value(), "Bearer", token.expiresAt(), UserView.from(saved));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(String username) {
        return userRepository.findByUsername(username)
                .map(UserView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", username));
    }
}
