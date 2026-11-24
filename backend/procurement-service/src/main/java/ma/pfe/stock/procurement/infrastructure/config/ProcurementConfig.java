package ma.pfe.stock.procurement.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ProcurementConfig {

    @Bean
    public ZoneId businessZoneId(ProcurementProperties properties) {
        return ZoneId.of(properties.zone());
    }

    /** Exécuteur dédié à la veille (longue : appels à l'IA et aux autres services). Une veille à la fois. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService watchExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "market-watch");
            t.setDaemon(true);
            return t;
        });
    }
}
