package ma.pfe.stock.stockmgmt.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class StockConfig {

    @Bean
    public ZoneId businessZoneId(StockProperties properties) {
        return ZoneId.of(properties.zone());
    }
}
