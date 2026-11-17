package ma.pfe.stock.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Microservice de reporting : agrège les données de product-service et stock-service
 * et produit des rapports PDF (OpenPDF) et Excel (Apache POI). Service sans base de données.
 */
@SpringBootApplication(scanBasePackages = "ma.pfe.stock")
@ConfigurationPropertiesScan
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}
