package ma.pfe.stock.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Microservice "achats intelligents" : veille du marché fournisseurs, demandes de prix (RFQ),
 * comparaison des devis, prédiction de l'évolution des prix et recommandations d'achat anticipé.
 */
@SpringBootApplication(scanBasePackages = "ma.pfe.stock")
@ConfigurationPropertiesScan
@EnableScheduling
public class ProcurementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcurementServiceApplication.class, args);
    }
}
