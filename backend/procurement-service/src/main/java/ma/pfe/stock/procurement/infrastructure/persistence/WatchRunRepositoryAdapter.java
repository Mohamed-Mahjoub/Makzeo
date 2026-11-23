package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.WatchRun;
import ma.pfe.stock.procurement.domain.port.WatchRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class WatchRunRepositoryAdapter implements WatchRunRepository {

    private final WatchRunJpaRepository jpa;

    public WatchRunRepositoryAdapter(WatchRunJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public WatchRun save(WatchRun run) {
        return jpa.saveAndFlush(WatchRunEntity.from(run)).toDomain();
    }

    @Override
    public Optional<WatchRun> findById(Long id) {
        return jpa.findById(id).map(WatchRunEntity::toDomain);
    }

    @Override
    public Optional<WatchRun> findLatest() {
        return jpa.findFirstByOrderByStartedAtDesc().map(WatchRunEntity::toDomain);
    }

    @Override
    public Optional<WatchRun> findRunning() {
        return jpa.findFirstByStatus(WatchRun.Status.RUNNING).map(WatchRunEntity::toDomain);
    }

    @Override
    public boolean existsCompleted(String period) {
        return jpa.existsByPeriodAndStatus(period, WatchRun.Status.COMPLETED);
    }

    @Override
    public PageResult<WatchRun> history(int page, int size) {
        Page<WatchRunEntity> result = jpa.findAllByOrderByStartedAtDesc(PageRequest.of(page, size));
        return PageResult.of(result.getContent().stream().map(WatchRunEntity::toDomain).toList(), page, size,
                result.getTotalElements());
    }
}
