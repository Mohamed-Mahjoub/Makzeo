package ma.pfe.stock.procurement.domain.model;

import java.time.Instant;

/** Exécution de la veille du marché (mensuelle, ou lancée à la demande). */
public class WatchRun {

    public enum Trigger { SCHEDULED, MANUAL }

    public enum Status { RUNNING, COMPLETED, FAILED }

    /** Compteurs d'une exécution. */
    public record Stats(int productsScanned, int suppliersDiscovered, int leadsFound, int rfqsDrafted,
                        int recommendations) {
    }

    private final Long id;
    private final String period;
    private final Trigger trigger;
    private Status status;
    private final String startedBy;
    private final Instant startedAt;
    private Instant finishedAt;
    private Stats stats;
    private String errorMessage;

    public WatchRun(Long id, String period, Trigger trigger, Status status, String startedBy, Instant startedAt,
                    Instant finishedAt, Stats stats, String errorMessage) {
        this.id = id;
        this.period = period;
        this.trigger = trigger;
        this.status = status;
        this.startedBy = startedBy;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.finishedAt = finishedAt;
        this.stats = stats != null ? stats : new Stats(0, 0, 0, 0, 0);
        this.errorMessage = errorMessage;
    }

    public static WatchRun start(String period, Trigger trigger, String user) {
        return new WatchRun(null, period, trigger, Status.RUNNING, user, Instant.now(), null, null, null);
    }

    public void complete(Stats stats, String warnings) {
        this.status = Status.COMPLETED;
        this.finishedAt = Instant.now();
        this.stats = stats;
        this.errorMessage = truncate(warnings);
    }

    public void fail(Stats stats, String error) {
        this.status = Status.FAILED;
        this.finishedAt = Instant.now();
        this.stats = stats;
        this.errorMessage = truncate(error == null ? "Erreur inconnue" : error);
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public Long getId() { return id; }
    public String getPeriod() { return period; }
    public Trigger getTrigger() { return trigger; }
    public Status getStatus() { return status; }
    public String getStartedBy() { return startedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Stats getStats() { return stats; }
    public String getErrorMessage() { return errorMessage; }
}
