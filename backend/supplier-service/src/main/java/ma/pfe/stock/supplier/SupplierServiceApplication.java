package ma.pfe.stock.supplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Microservice de gestion des fournisseurs. */
@SpringBootApplication(scanBasePackages = "ma.pfe.stock")
@ConfigurationPropertiesScan
public class SupplierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
