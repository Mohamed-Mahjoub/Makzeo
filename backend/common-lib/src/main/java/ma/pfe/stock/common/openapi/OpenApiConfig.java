package ma.pfe.stock.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI 3 commune à tous les microservices (springdoc-openapi).
 * <ul>
 *   <li>Swagger UI : {@code http://localhost:<port>/swagger-ui.html}</li>
 *   <li>Spécification JSON : {@code http://localhost:<port>/v3/api-docs}</li>
 * </ul>
 * Le schéma de sécurité "bearer-jwt" active le bouton <b>Authorize</b> : on y colle le jeton
 * obtenu via {@code POST /api/auth/login} (sur auth-service), sans le préfixe "Bearer".
 */
@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI stockManagementOpenApi(@Value("${spring.application.name}") String applicationName,
                                          @Value("${app.openapi.description:API REST du microservice}")
                                          String description,
                                          @Value("${info.app.version:1.0.0}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Makzeo — " + applicationName)
                        .description(description)
                        .version(version)
                        .contact(new Contact().name("Makzeo — gestion de produits et de stock (PFE)")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Jeton JWT obtenu via POST /api/auth/login (auth-service, port 8081)")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
