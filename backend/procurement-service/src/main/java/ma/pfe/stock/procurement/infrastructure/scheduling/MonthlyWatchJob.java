package ma.pfe.stock.procurement.infrastructure.scheduling;

import ma.pfe.stock.procurement.application.service.MarketWatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenchement mensuel de la veille (par défaut le 1er du mois à 6 h 30, heure de Tunis).
 * Avec plusieurs réplicas, l'index unique sur les veilles "en cours" garantit une seule exécution.
 */
@Component
public class MonthlyWatchJob {

    private final MarketWatchService watch;

    public MonthlyWatchJob(MarketWatchService watch) {
        this.watch = watch;
    }

    @Scheduled(cron = "${app.procurement.watch-cron:0 30 6 1 * *}", zone = "${app.procurement.zone:Africa/Tunis}")
    public void run() {
        watch.startScheduled();
    }
}
