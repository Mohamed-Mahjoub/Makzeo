package ma.pfe.stock.procurement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.pfe.stock.procurement.domain.model.WatchRun;

import java.time.Instant;

@Entity
@Table(name = "watch_runs")
public class WatchRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 7)
    private String period;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WatchRun.Trigger triggerType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WatchRun.Status status;
    @Column(nullable = false, length = 50)
    private String startedBy;
    @Column(nullable = false)
    private Instant startedAt;
    private Instant finishedAt;
    @Column(nullable = false)
    private int productsScanned;
    @Column(nullable = false)
    private int suppliersDiscovered;
    @Column(nullable = false)
    private int leadsFound;
    @Column(nullable = false)
    private int rfqsDrafted;
    @Column(nullable = false)
    private int recommendations;
    @Column(length = 1000)
    private String errorMessage;

    protected WatchRunEntity() {
    }

    static WatchRunEntity from(WatchRun r) {
        WatchRunEntity e = new WatchRunEntity();
        e.id = r.getId();
        e.period = r.getPeriod();
        e.triggerType = r.getTrigger();
        e.status = r.getStatus();
        e.startedBy = r.getStartedBy();
        e.startedAt = r.getStartedAt();
        e.finishedAt = r.getFinishedAt();
        e.productsScanned = r.getStats().productsScanned();
        e.suppliersDiscovered = r.getStats().suppliersDiscovered();
        e.leadsFound = r.getStats().leadsFound();
        e.rfqsDrafted = r.getStats().rfqsDrafted();
        e.recommendations = r.getStats().recommendations();
        e.errorMessage = r.getErrorMessage();
        return e;
    }

    WatchRun toDomain() {
        return new WatchRun(id, period, triggerType, status, startedBy, startedAt, finishedAt,
                new WatchRun.Stats(productsScanned, suppliersDiscovered, leadsFound, rfqsDrafted, recommendations),
                errorMessage);
    }
}
