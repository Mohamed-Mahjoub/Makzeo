package ma.pfe.stock.auth.application.service;

import ma.pfe.stock.auth.application.dto.CreateUserCommand;
import ma.pfe.stock.auth.application.dto.UpdateUserCommand;
import ma.pfe.stock.auth.application.dto.UserView;
import ma.pfe.stock.auth.application.port.PasswordHasher;
import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.domain.port.UserRepository;
import ma.pfe.stock.common.exception.BusinessRuleException;
import ma.pfe.stock.common.exception.DuplicateResourceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.common.pagination.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cas d'utilisation : administration des utilisateurs et gestion des mots de passe. */
@Service
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public UserView create(CreateUserCommand command) {
        String username = command.username().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Le nom d'utilisateur '" + username + "' existe déjà");
        }
        if (userRepository.existsByEmail(command.email().trim().toLowerCase())) {
            throw new DuplicateResourceException("L'email '" + command.email() + "' est déjà utilisé");
        }
        validatePassword(command.password());
        User user = User.create(username, command.email(), command.fullName(),
                passwordHasher.hash(command.password()), command.roles());
        return UserView.from(userRepository.save(user));
    }

    @Transactional
    public UserView update(Long id, UpdateUserCommand command, String currentUsername) {
        User user = getUser(id);
        if (userRepository.existsByEmailAndIdNot(command.email().trim().toLowerCase(), id)) {
            throw new DuplicateResourceException("L'email '" + command.email() + "' est déjà utilisé");
        }
        if (user.getUsername().equals(currentUsername) && user.hasRole(Role.ADMIN)
                && !command.roles().contains(Role.ADMIN)) {
            throw new BusinessRuleException("SELF_DEMOTION", "Vous ne pouvez pas retirer votre propre rôle ADMIN");
        }
        user.updateProfile(command.email(), command.fullName(), command.roles());
        return UserView.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserView findById(Long id) {
        return UserView.from(getUser(id));
    }

    @Transactional(readOnly = true)
    public PageResult<UserView> search(String keyword, int page, int size) {
        return userRepository.search(keyword, page, size).map(UserView::from);
    }

    @Transactional
    public UserView setEnabled(Long id, boolean enabled, String currentUsername) {
        User user = getUser(id);
        if (!enabled && user.getUsername().equals(currentUsername)) {
            throw new BusinessRuleException("SELF_DISABLE", "Vous ne pouvez pas désactiver votre propre compte");
        }
        if (enabled) {
            user.enable();
        } else {
            user.disable();
        }
        return UserView.from(userRepository.save(user));
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = getUser(id);
        validatePassword(newPassword);
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", username));
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("WRONG_PASSWORD", "Le mot de passe actuel est incorrect");
        }
        validatePassword(newPassword);
        user.changePassword(passwordHasher.hash(newPassword));
        userRepository.save(user);
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("WEAK_PASSWORD",
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        }
    }
}
