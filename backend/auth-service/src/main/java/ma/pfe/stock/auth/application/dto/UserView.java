package ma.pfe.stock.auth.application.dto;

import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.model.User;

import java.time.Instant;
import java.util.Set;

/** Vue d'un utilisateur exposée par l'API (sans mot de passe). */
public record UserView(Long id, String username, String email, String fullName, Set<Role> roles,
                       boolean enabled, Instant createdAt, Instant lastLoginAt) {

    public static UserView from(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.getRoles(), user.isEnabled(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
