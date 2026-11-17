package ma.pfe.stock.auth.application.dto;

import ma.pfe.stock.auth.domain.model.Role;

import java.util.Set;

public record UpdateUserCommand(String email, String fullName, Set<Role> roles) {
}
