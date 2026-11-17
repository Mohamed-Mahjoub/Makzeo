package ma.pfe.stock.reporting.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class ReportingConfig {

    @Bean
    public ZoneId businessZoneId(ReportingProperties properties) {
        return ZoneId.of(properties.zone());
    }
}
