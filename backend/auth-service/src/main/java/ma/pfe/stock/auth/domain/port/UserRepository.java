package ma.pfe.stock.auth.domain.port;

import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.common.pagination.PageResult;

import java.util.Optional;

/** Port de sortie (persistance des utilisateurs), implémenté par la couche infrastructure. */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    long count();

    PageResult<User> search(String keyword, int page, int size);
}
