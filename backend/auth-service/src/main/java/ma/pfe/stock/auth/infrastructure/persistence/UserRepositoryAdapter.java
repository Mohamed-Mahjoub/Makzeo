package ma.pfe.stock.auth.infrastructure.persistence;

import ma.pfe.stock.auth.domain.model.User;
import ma.pfe.stock.auth.domain.port.UserRepository;
import ma.pfe.stock.common.pagination.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;

/** Adaptateur : implémente le port UserRepository avec Spring Data JPA. */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        UserEntity entity = user.getId() == null
                ? new UserEntity()
                : jpa.findById(user.getId()).orElseGet(UserEntity::new);
        entity.setUsername(user.getUsername());
        entity.setEmail(user.getEmail());
        entity.setFullName(user.getFullName());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRoles(new HashSet<>(user.getRoles()));
        entity.setEnabled(user.isEnabled());
        entity.setCreatedAt(user.getCreatedAt());
        entity.setLastLoginAt(user.getLastLoginAt());
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(this::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        return jpa.existsByEmailAndIdNot(email, id);
    }

    @Override
    public long count() {
        return jpa.count();
    }

    @Override
    public PageResult<User> search(String keyword, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<UserEntity> result = jpa.search(kw, PageRequest.of(page, size, Sort.by("username")));
        return PageResult.of(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements());
    }

    private User toDomain(UserEntity e) {
        return new User(e.getId(), e.getUsername(), e.getEmail(), e.getFullName(), e.getPasswordHash(),
                e.getRoles(), e.isEnabled(), e.getCreatedAt(), e.getLastLoginAt());
    }
}
