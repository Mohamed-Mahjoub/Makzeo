package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.WatchRun;

import java.util.Optional;

public interface WatchRunRepository {

    /** Enregistre ; refuse (exception d'intégrité) une deuxième exécution simultanée. */
    WatchRun save(WatchRun run);

    Optional<WatchRun> findById(Long id);

    Optional<WatchRun> findLatest();

    Optional<WatchRun> findRunning();

    boolean existsCompleted(String period);

    PageResult<WatchRun> history(int page, int size);
}
