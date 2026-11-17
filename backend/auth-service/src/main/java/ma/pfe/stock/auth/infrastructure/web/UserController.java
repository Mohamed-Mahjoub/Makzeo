package ma.pfe.stock.auth.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.pfe.stock.auth.application.dto.CreateUserCommand;
import ma.pfe.stock.auth.application.dto.UpdateUserCommand;
import ma.pfe.stock.auth.application.dto.UserView;
import ma.pfe.stock.auth.application.service.UserService;
import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API d'administration des utilisateurs : /api/users (rôle ADMIN). */
@RestController
@Tag(name = "Utilisateurs", description = "Administration des comptes et des rôles (ADMIN)")
@RequestMapping("/api/users")
@PreAuthorize(Roles.IS_ADMIN)
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Rechercher des utilisateurs", description = "Filtre q sur le nom, l'email ou le nom d'utilisateur ; résultat paginé.")
    @GetMapping
    public PageResult<UserView> search(@RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return userService.search(q, page, size);
    }

    @Operation(summary = "Détail d'un utilisateur")
    @GetMapping("/{id}")
    public UserView findById(@PathVariable Long id) {
        return userService.findById(id);
    }

    @Operation(summary = "Créer un utilisateur")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody AuthRequests.CreateUserRequest request) {
        return userService.create(new CreateUserCommand(request.username(), request.email(), request.fullName(),
                request.password(), request.roles()));
    }

    @Operation(summary = "Modifier email, nom complet et rôles")
    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody AuthRequests.UpdateUserRequest request) {
        return userService.update(id, new UpdateUserCommand(request.email(), request.fullName(), request.roles()),
                CurrentUser.username());
    }

    @Operation(summary = "Activer un compte")
    @PatchMapping("/{id}/enable")
    public UserView enable(@PathVariable Long id) {
        return userService.setEnabled(id, true, CurrentUser.username());
    }

    @Operation(summary = "Désactiver un compte")
    @PatchMapping("/{id}/disable")
    public UserView disable(@PathVariable Long id) {
        return userService.setEnabled(id, false, CurrentUser.username());
    }

    @Operation(summary = "Réinitialiser le mot de passe")
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody AuthRequests.ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
