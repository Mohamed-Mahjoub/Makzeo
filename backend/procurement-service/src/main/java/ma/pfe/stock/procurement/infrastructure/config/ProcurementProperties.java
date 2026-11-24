package ma.pfe.stock.procurement.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Paramètres métier du module achats (préfixe {@code app.procurement}).
 *
 * @param zone                    fuseau horaire métier
 * @param currency                devise des prix
 * @param marketCountry           pays du marché surveillé (code ISO, transmis à la recherche web)
 * @param marketName              nom du marché, utilisé dans les consignes de l'agent
 * @param vatRatePct              taux de TVA pour ramener un prix TTC publié en HT
 * @param watchEnabled            active la veille planifiée
 * @param watchCron               planification de la veille (par défaut : le 1er de chaque mois à 6 h 30)
 * @param maxProductsPerRun       articles analysés par la veille (maîtrise du coût de l'API)
 * @param maxSuppliersPerProduct  fournisseurs recherchés par article
 * @param maxRfqsPerProduct       demandes de prix préparées par article et par veille
 * @param consumptionLookbackDays période d'observation de la consommation
 * @param forecastHorizonMonths   horizon des prédictions de prix
 * @param annualInflationPct      inflation annuelle de référence (à mettre à jour selon l'INS)
 * @param marketSignalWeight      poids du signal de marché de l'agent dans la prédiction (0 à 1)
 * @param monthlyHoldingRate      coût de stockage mensuel en fraction de la valeur
 * @param maxCoverMonths          couverture maximale d'un achat anticipé
 * @param minChangePct            hausse prévue minimale (%) pour proposer un achat anticipé
 * @param minSwitchSavingPct      économie minimale (%) pour proposer un changement de fournisseur
 * @param purchaseBudget          budget maximal d'un achat anticipé (vide : sans limite)
 * @param rfqResponseDays         délai de réponse demandé aux fournisseurs
 * @param buyerCompany            raison sociale de l'acheteur, signée dans les demandes de prix
 * @param buyerEmail              adresse d'expédition et de réponse des demandes de prix
 * @param purchasingManagerEmails destinataires des alertes d'opportunité
 * @param appUrl                  adresse de l'application, rappelée dans les alertes
 */
@ConfigurationProperties(prefix = "app.procurement")
public record ProcurementProperties(
        @DefaultValue("http://localhost:8082") String productServiceUrl,
        @DefaultValue("http://localhost:8083") String supplierServiceUrl,
        @DefaultValue("http://localhost:8084") String stockServiceUrl,
        @DefaultValue("Africa/Tunis") String zone,
        @DefaultValue("TND") String currency,
        @DefaultValue("TN") String marketCountry,
        @DefaultValue("Tunisie") String marketName,
        @DefaultValue("19") double vatRatePct,
        @DefaultValue("true") boolean watchEnabled,
        @DefaultValue("0 30 6 1 * *") String watchCron,
        @DefaultValue("25") int maxProductsPerRun,
        @DefaultValue("5") int maxSuppliersPerProduct,
        @DefaultValue("3") int maxRfqsPerProduct,
        @DefaultValue("180") int consumptionLookbackDays,
        @DefaultValue("3") int forecastHorizonMonths,
        @DefaultValue("5.5") double annualInflationPct,
        @DefaultValue("0.3") double marketSignalWeight,
        @DefaultValue("0.015") double monthlyHoldingRate,
        @DefaultValue("12") int maxCoverMonths,
        @DefaultValue("3") double minChangePct,
        @DefaultValue("5") double minSwitchSavingPct,
        BigDecimal purchaseBudget,
        @DefaultValue("14") int rfqResponseDays,
        @DefaultValue("Makzeo") String buyerCompany,
        @DefaultValue("achats@makzeo.local") String buyerEmail,
        @DefaultValue List<String> purchasingManagerEmails,
        @DefaultValue("http://localhost:8080") String appUrl) {
}
