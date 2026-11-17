package ma.pfe.stock.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservice d'authentification : émission des jetons JWT et gestion des utilisateurs.
 * Le scan couvre "ma.pfe.stock" afin d'inclure la configuration commune (common-lib).
 */
@SpringBootApplication(scanBasePackages = "ma.pfe.stock")
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
