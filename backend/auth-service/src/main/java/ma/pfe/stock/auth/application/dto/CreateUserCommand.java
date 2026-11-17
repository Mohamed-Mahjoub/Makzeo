package ma.pfe.stock.auth.application.dto;

import ma.pfe.stock.auth.domain.model.Role;

import java.util.Set;

public record CreateUserCommand(String username, String email, String fullName, String password, Set<Role> roles) {
}
