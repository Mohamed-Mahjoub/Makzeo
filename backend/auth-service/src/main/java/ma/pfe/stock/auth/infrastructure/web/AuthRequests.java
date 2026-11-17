package ma.pfe.stock.auth.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.auth.domain.model.Role;

import java.util.Set;

/** Corps des requêtes HTTP (validation Bean Validation). */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record LoginRequest(
            @NotBlank(message = "Le nom d'utilisateur est obligatoire") String username,
            @NotBlank(message = "Le mot de passe est obligatoire") String password) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Caractères autorisés : lettres, chiffres, . _ -")
            String username,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotEmpty(message = "Au moins un rôle est requis") Set<Role> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Size(max = 120) String fullName,
            @NotEmpty(message = "Au moins un rôle est requis") Set<Role> roles) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }
}
