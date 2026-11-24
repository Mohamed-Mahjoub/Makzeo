package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.WatchRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatchRunJpaRepository extends JpaRepository<WatchRunEntity, Long> {

    Optional<WatchRunEntity> findFirstByOrderByStartedAtDesc();

    Optional<WatchRunEntity> findFirstByStatus(WatchRun.Status status);

    boolean existsByPeriodAndStatus(String period, WatchRun.Status status);

    Page<WatchRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
