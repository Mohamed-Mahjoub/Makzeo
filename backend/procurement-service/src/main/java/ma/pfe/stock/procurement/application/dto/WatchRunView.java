package ma.pfe.stock.procurement.application.dto;

import ma.pfe.stock.procurement.domain.model.WatchRun;

import java.time.Instant;

public record WatchRunView(Long id, String period, WatchRun.Trigger trigger, WatchRun.Status status, String startedBy,
                           Instant startedAt, Instant finishedAt, int productsScanned, int suppliersDiscovered,
                           int leadsFound, int rfqsDrafted, int recommendations, String message) {

    public static WatchRunView from(WatchRun r) {
        return new WatchRunView(r.getId(), r.getPeriod(), r.getTrigger(), r.getStatus(), r.getStartedBy(),
                r.getStartedAt(), r.getFinishedAt(), r.getStats().productsScanned(), r.getStats().suppliersDiscovered(),
                r.getStats().leadsFound(), r.getStats().rfqsDrafted(), r.getStats().recommendations(),
                r.getErrorMessage());
    }
}
