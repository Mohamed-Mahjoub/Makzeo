package ma.pfe.stock.auth.infrastructure.config;

import ma.pfe.stock.auth.application.dto.CreateUserCommand;
import ma.pfe.stock.auth.application.service.UserService;
import ma.pfe.stock.auth.domain.model.Role;
import ma.pfe.stock.auth.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Crée le compte administrateur initial au premier démarrage (base vide).
 * Le mot de passe est fourni par variable d'environnement (Secret Kubernetes).
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthProperties properties;

    public AdminAccountInitializer(UserRepository userRepository, UserService userService, AuthProperties properties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        AuthProperties.Admin admin = properties.admin();
        if (admin.password() == null || admin.password().isBlank()) {
            log.warn("Aucun utilisateur en base et app.auth.admin.password non défini : compte admin non créé");
            return;
        }
        userService.create(new CreateUserCommand(admin.username(), admin.email(), admin.fullName(),
                admin.password(), Set.of(Role.ADMIN)));
        log.info("Compte administrateur initial '{}' créé", admin.username());
    }
}
