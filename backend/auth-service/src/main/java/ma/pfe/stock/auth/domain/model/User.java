package ma.pfe.stock.auth.domain.model;

import ma.pfe.stock.common.exception.BusinessRuleException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entité métier "Utilisateur" (couche domaine, sans dépendance technique).
 */
public class User {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String passwordHash;
    private Set<Role> roles;
    private boolean enabled;
    private Instant createdAt;
    private Instant lastLoginAt;

    public User(Long id, String username, String email, String fullName, String passwordHash,
                Set<Role> roles, boolean enabled, Instant createdAt, Instant lastLoginAt) {
        this.id = id;
        this.username = requireText(username, "username").toLowerCase();
        this.email = requireText(email, "email").toLowerCase();
        this.fullName = requireText(fullName, "fullName");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        setRoles(roles);
        this.enabled = enabled;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.lastLoginAt = lastLoginAt;
    }

    public static User create(String username, String email, String fullName, String passwordHash, Set<Role> roles) {
        return new User(null, username, email, fullName, passwordHash, roles, true, Instant.now(), null);
    }

    public void updateProfile(String email, String fullName, Set<Role> roles) {
        this.email = requireText(email, "email").toLowerCase();
        this.fullName = requireText(fullName, "fullName");
        setRoles(roles);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    private void setRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BusinessRuleException("USER_ROLE_REQUIRED", "Un utilisateur doit avoir au moins un rôle");
        }
        this.roles = EnumSet.copyOf(roles);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le champ '" + field + "' est obligatoire");
        }
        return value.trim();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return EnumSet.copyOf(roles); }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
