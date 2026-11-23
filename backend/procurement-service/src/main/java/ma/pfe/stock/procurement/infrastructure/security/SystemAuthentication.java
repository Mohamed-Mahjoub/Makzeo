package ma.pfe.stock.procurement.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ma.pfe.stock.common.security.AppSecurityProperties;
import ma.pfe.stock.common.security.Roles;
import ma.pfe.stock.common.security.SecurityConfig;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Identité technique de l'agent pour les traitements planifiés (aucun utilisateur connecté) :
 * un jeton JWT de courte durée, rôle GESTIONNAIRE, signé avec la clé partagée. Les autres services
 * appliquent leurs règles d'autorisation habituelles à ce jeton.
 */
@Component
public class SystemAuthentication {

    public static final String AGENT_USER = "agent-achats";
    private static final Duration VALIDITY = Duration.ofHours(3);

    private final JwtEncoder encoder;
    private final AppSecurityProperties properties;

    public SystemAuthentication(SecretKey jwtSecretKey, AppSecurityProperties properties) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(jwtSecretKey));
        this.properties = properties;
    }

    public <T> T callAsAgent(Supplier<T> action) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwtIssuer())
                .subject(AGENT_USER)
                .issuedAt(now)
                .expiresAt(now.plus(VALIDITY))
                .claim("name", "Agent achats (veille automatique)")
                .claim(SecurityConfig.ROLES_CLAIM, List.of(Roles.GESTIONNAIRE))
                .build();
        Jwt jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + Roles.GESTIONNAIRE)), AGENT_USER);

        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    public void runAsAgent(Runnable action) {
        callAsAgent(() -> {
            action.run();
            return null;
        });
    }
}
