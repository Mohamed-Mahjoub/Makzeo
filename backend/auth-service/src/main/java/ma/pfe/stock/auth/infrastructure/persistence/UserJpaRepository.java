package ma.pfe.stock.auth.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("""
            select u from UserEntity u
            where :keyword is null
               or lower(u.username) like lower(concat('%', cast(:keyword as string), '%'))
               or lower(u.email) like lower(concat('%', cast(:keyword as string), '%'))
               or lower(u.fullName) like lower(concat('%', cast(:keyword as string), '%'))
            """)
    Page<UserEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
