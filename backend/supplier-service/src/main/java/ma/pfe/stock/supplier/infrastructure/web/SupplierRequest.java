package ma.pfe.stock.supplier.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.pfe.stock.supplier.application.dto.SupplierCommand;

public record SupplierRequest(
        @NotBlank(message = "Le code est obligatoire") @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code : lettres, chiffres, _ et - uniquement") String code,
        @NotBlank(message = "La raison sociale est obligatoire") @Size(max = 150) String name,
        @Size(max = 120) String contactName,
        @Email @Size(max = 120) String email,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 ().-]*$", message = "Numéro de téléphone invalide") String phone,
        @Size(max = 255) String address,
        @Size(max = 80) String city,
        @Pattern(regexp = "^([A-Za-z]{2})?$", message = "Code pays ISO sur 2 lettres (ex. MA, TN, FR)") String country,
        @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9 ./-]*$",
                message = "Identifiant fiscal : lettres, chiffres, espace . / -") String taxId,
        @Size(max = 1000) String notes) {

    public SupplierCommand toCommand() {
        return new SupplierCommand(code, name, contactName, email, phone, address, city, country, taxId, notes);
    }
}
