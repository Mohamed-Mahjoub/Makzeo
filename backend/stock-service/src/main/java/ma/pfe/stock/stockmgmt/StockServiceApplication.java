package ma.pfe.stock.stockmgmt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Microservice Stock : entrées/sorties, niveaux de stock, inventaires, alertes et historique. */
@SpringBootApplication(scanBasePackages = "ma.pfe.stock")
@ConfigurationPropertiesScan
public class StockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
