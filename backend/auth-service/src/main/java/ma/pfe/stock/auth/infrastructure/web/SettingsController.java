package ma.pfe.stock.auth.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ma.pfe.stock.auth.application.dto.CurrencyView;
import ma.pfe.stock.auth.application.dto.SettingsView;
import ma.pfe.stock.auth.application.service.SettingsService;
import ma.pfe.stock.common.security.CurrentUser;
import ma.pfe.stock.common.security.Roles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Paramètres de l'application : /api/settings. */
@RestController
@Tag(name = "Paramètres", description = "Devise de l'application (choisie par l'administrateur)")
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public record CurrencyRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "Code ISO 4217 sur 3 lettres (ex. TND)")
            String currency) {
    }

    @Operation(summary = "Paramètres de l'application", description = "Devise courante, utilisée par tous les écrans et rapports.")
    @GetMapping
    @PreAuthorize(Roles.CAN_READ)
    public SettingsView get() {
        return settingsService.get();
    }

    @Operation(summary = "Changer la devise de l'application",
            description = "Aucune conversion n'est appliquée : les prix existants sont réinterprétés dans la nouvelle devise.")
    @PutMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public SettingsView update(@Valid @RequestBody CurrencyRequest request) {
        return settingsService.changeCurrency(request.currency(), CurrentUser.username());
    }

    @Operation(summary = "Devises disponibles", description = "Toutes les devises ISO 4217 en vigueur, les plus courantes en premier.")
    @GetMapping("/currencies")
    @PreAuthorize(Roles.CAN_READ)
    public List<CurrencyView> currencies() {
        return settingsService.availableCurrencies();
    }
}
