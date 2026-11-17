package ma.pfe.stock.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsJpaRepository extends JpaRepository<SettingsEntity, Long> {
}
