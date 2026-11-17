package ma.pfe.stock.auth.application.port;

/** Port : hachage des mots de passe (implémenté avec BCrypt). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
