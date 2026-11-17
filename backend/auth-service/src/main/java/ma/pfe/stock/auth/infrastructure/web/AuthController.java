package ma.pfe.stock.auth.infrastructure.web;

import jakarta.validation.Valid;
import ma.pfe.stock.auth.application.dto.AuthResult;
import ma.pfe.stock.auth.application.dto.UserView;
import ma.pfe.stock.auth.application.service.AuthService;
import ma.pfe.stock.auth.application.service.UserService;
import ma.pfe.stock.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API d'authentification : /api/auth. */
@RestController
@Tag(name = "Authentification", description = "Connexion (jeton JWT), profil et mot de passe de l'utilisateur connecté")
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** Public : retourne un jeton JWT. */
    @Operation(summary = "Se connecter", description = "Retourne un jeton JWT à utiliser dans le bouton Authorize (public).")
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody AuthRequests.LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @Operation(summary = "Profil de l'utilisateur connecté")
    @GetMapping("/me")
    public UserView me() {
        return authService.currentUser(CurrentUser.username());
    }

    @Operation(summary = "Changer son mot de passe")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody AuthRequests.ChangePasswordRequest request) {
        userService.changeOwnPassword(CurrentUser.username(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
