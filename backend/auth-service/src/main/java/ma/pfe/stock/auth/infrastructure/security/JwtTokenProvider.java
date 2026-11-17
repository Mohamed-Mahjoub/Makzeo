package ma.pfe.stock.auth.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import ma.pfe.stock.auth.application.port.TokenProvider;
import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.infrastructure.config.AuthProperties;
import ma.pfe.stock.common.security.AppSecurityProperties;
import ma.pfe.stock.common.security.SecurityConfig;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;

/** Adaptateur JWT (HS256) du port TokenProvider. */
@Component
public class JwtTokenProvider implements TokenProvider {

    private final JwtEncoder encoder;
    private final AppSecurityProperties securityProperties;
    private final AuthProperties authProperties;

    public JwtTokenProvider(SecretKey jwtSecretKey, AppSecurityProperties securityProperties,
                            AuthProperties authProperties) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
        this.securityProperties = securityProperties;
        this.authProperties = authProperties;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(authProperties.tokenValidity());
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.jwtIssuer())
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim(SecurityConfig.ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
