package ma.pfe.stock.procurement.infrastructure.websearch;

import ma.pfe.stock.procurement.application.port.MarketIntelligence;
import ma.pfe.stock.procurement.domain.model.Equivalence;
import ma.pfe.stock.procurement.domain.model.ProductInfo;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import ma.pfe.stock.procurement.infrastructure.config.WebSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Veille fournisseurs sans modèle de langage : Serper cherche l'article sur Google (Shopping et pages web
 * du marché), Tavily lit les pages trouvées pour y relever le prix et les coordonnées du vendeur.
 * Les règles sont déterministes et explicables : article identique si sa référence constructeur ou son
 * code-barres figure sur la page, équivalent si la désignation partage assez de mots-clés.
 */
@Component
public class WebSearchMarketIntelligence implements MarketIntelligence {

    private static final Logger log = LoggerFactory.getLogger(WebSearchMarketIntelligence.class);

    private final SerperClient serper;
    private final TavilyClient tavily;
    private final WebSearchProperties web;
    private final ProcurementProperties properties;

    public WebSearchMarketIntelligence(SerperClient serper, TavilyClient tavily, WebSearchProperties web,
                                       ProcurementProperties properties) {
        this.serper = serper;
        this.tavily = tavily;
        this.web = web;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return serper.configured();
    }

    @Override
    public String provider() {
        return tavily.configured() ? "Serper + Tavily" : "Serper";
    }

    /** Candidat issu d'un résultat de recherche, complété par la lecture de sa page. */
    private static final class Candidate {
        final String host;
        final String link;
        String name;
        String title;
        String snippet;
        BigDecimal price;
        boolean priceIncludesVat = true;
        String priceOrigin;
        String email;
        String phone;
        String city;
        String page;

        Candidate(String host, String link) {
            this.host = host;
            this.link = link;
        }
    }

    @Override
    public List<SupplierFinding> findSuppliers(ProductInfo product, int maxResults) {
        if (!enabled()) {
            return List.of();
        }
        String query = query(product);
        Map<String, Candidate> byHost = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        // 1. Google Shopping : offres avec prix et marchand
        for (SerperClient.Hit h : hits(() -> serper.shopping(query), errors)) {
            Candidate c = candidate(byHost, h);
            if (c != null && c.price == null && h.priceText() != null && mentionsDinar(h.priceText())) {
                WebText.parseAmount(h.priceText()).filter(p -> WebText.plausible(p, product.purchasePrice()))
                        .ifPresent(p -> { c.price = p; c.priceOrigin = "Google Shopping"; });
            }
        }
        // 2. Recherche web : pages des revendeurs du marché
        for (SerperClient.Hit h : hits(() -> serper.search(query + " prix " + properties.marketName()), errors)) {
            Candidate c = candidate(byHost, h);
            if (c != null && c.price == null && h.snippet() != null) {
                WebText.productPrice(h.snippet(), anchors(product), product.purchasePrice())
                        .ifPresent(p -> { c.price = p.amount(); c.priceIncludesVat = p.includesVat(); c.priceOrigin = "extrait Google"; });
            }
        }

        if (errors.size() == 2) {
            throw new WebSearchException(errors.get(0), null); // Serper indisponible : signalé dans la veille
        }

        // 3. Tavily : lecture des pages les plus pertinentes (prix, e-mail, téléphone, ville)
        List<Candidate> ranked = new ArrayList<>(byHost.values());
        ranked.sort(Comparator.comparingInt((Candidate c) -> -score(product, c.title)));
        List<Candidate> toRead = ranked.stream().limit(Math.max(0, web.maxPagesPerProduct())).toList();
        Map<String, String> pages;
        try {
            pages = tavily.extract(toRead.stream().map(c -> c.link).toList());
        } catch (WebSearchException e) {
            log.warn("Veille web {} : lecture des pages impossible ({})", product.reference(), e.getMessage());
            pages = Map.of();
        }
        for (Candidate c : toRead) {
            String page = pages.get(c.link);
            if (page == null) {
                continue;
            }
            c.page = page;
            if (c.price == null) {
                WebText.productPrice(page, anchors(product), product.purchasePrice()).ifPresent(p -> {
                    c.price = p.amount();
                    c.priceIncludesVat = p.includesVat();
                    c.priceOrigin = "page du fournisseur";
                });
            }
            c.email = WebText.email(page, c.host);
            c.phone = WebText.phone(page);
            c.city = WebText.city(page);
        }

        // 4. Identique, équivalent ou écarté
        List<SupplierFinding> findings = new ArrayList<>();
        for (Candidate c : ranked) {
            String text = c.title + " " + (c.snippet == null ? "" : c.snippet) + " " + (c.page == null ? "" : c.page);
            boolean sameCode = WebText.containsCode(text, product.barcode()) || WebText.containsCode(text, product.reference());
            int similarity = Math.max(WebText.similarity(product.name(), c.title), sameCode ? 95 : 0);
            if (!sameCode && similarity < web.minSimilarity()) {
                continue;
            }
            Equivalence equivalence = sameCode || similarity >= 90 ? Equivalence.IDENTICAL : Equivalence.EQUIVALENT;
            findings.add(new SupplierFinding(cut(c.name, 150), "https://" + c.host, c.email, cut(c.phone, 40),
                    c.city, cut(c.title, 255), sameCode ? cut(product.reference(), 100) : null, cut(c.link, 1000),
                    c.price, properties.currency(), c.priceIncludesVat, equivalence, similarity,
                    cut(rationale(product, c, sameCode, similarity), 1000)));
            if (findings.size() >= maxResults) {
                break;
            }
        }
        log.info("Veille web {} : {} résultat(s), {} page(s) lue(s), {} fournisseur(s) retenu(s)", product.reference(),
                byHost.size(), pages.size(), findings.size());
        return findings;
    }

    /** Pas de tendance de marché sans modèle de langage : la prévision reste statistique. */
    @Override
    public Optional<MarketSignal> marketSignal(ProductInfo product, int horizonMonths) {
        return Optional.empty();
    }

    /** Pas de rédaction automatique : le modèle de texte de l'application est utilisé. */
    @Override
    public RfqText writeRfq(RfqRequest request) {
        throw new UnsupportedOperationException("Rédaction assurée par le modèle de texte");
    }

    /** La lecture des devis sans IA est assurée par le lecteur de texte de l'application. */
    @Override
    public Optional<ExtractedQuote> extractQuote(String quoteText, ProductInfo product) {
        return Optional.empty();
    }

    // ------------------------------------------------------------------ règles

    private Candidate candidate(Map<String, Candidate> byHost, SerperClient.Hit h) {
        String host = WebText.host(h.link());
        if (host == null || excluded(host)) {
            return null;
        }
        String text = h.title() + " " + (h.snippet() == null ? "" : h.snippet());
        boolean local = host.endsWith("." + properties.marketCountry().toLowerCase(Locale.ROOT))
                || h.priceText() != null || mentionsDinar(text) || text.contains(properties.marketName());
        if (!local) {
            return null; // site hors du marché visé
        }
        Candidate c = byHost.get(host);
        if (c == null) {
            c = new Candidate(host, h.link());
            c.name = h.source() != null && !h.source().contains(".") ? h.source() : WebText.nameFromHost(host);
            c.title = h.title();
            c.snippet = h.snippet();
            byHost.put(host, c);
        }
        return c;
    }

    private boolean excluded(String host) {
        return web.excludedDomains().stream().anyMatch(d -> host.equals(d) || host.endsWith("." + d));
    }

    private static boolean mentionsDinar(String text) {
        return text != null && text.matches("(?is).*\\b(TND|DT|dinars?)\\b.*");
    }

    private static int score(ProductInfo product, String title) {
        int s = WebText.similarity(product.name(), title);
        if (WebText.containsCode(title, product.reference())) {
            s += 100;
        }
        return s;
    }

    private static List<String> anchors(ProductInfo product) {
        List<String> a = new ArrayList<>();
        a.add(product.reference());
        a.add(product.barcode());
        a.add(product.name().length() > 30 ? product.name().substring(0, 30) : product.name());
        return a;
    }

    private static String query(ProductInfo product) {
        String q = product.name();
        if (product.reference() != null && product.reference().matches(".*\\d.*")
                && !q.toLowerCase(Locale.ROOT).contains(product.reference().toLowerCase(Locale.ROOT))) {
            q += " " + product.reference();
        }
        return q.length() > 120 ? q.substring(0, 120) : q;
    }

    private String rationale(ProductInfo product, Candidate c, boolean sameCode, int similarity) {
        StringBuilder sb = new StringBuilder();
        if (sameCode) {
            sb.append("Référence ").append(product.reference()).append(" trouvée chez ").append(c.name).append('.');
        } else {
            sb.append("Article équivalent : ").append(similarity)
                    .append(" % des mots-clés de la désignation se retrouvent dans « ").append(c.title).append(" ».");
        }
        if (c.price != null) {
            sb.append(" Prix ").append(c.price.toPlainString()).append(' ').append(properties.currency())
                    .append(c.priceIncludesVat ? " TTC" : " HT").append(" relevé sur ").append(c.priceOrigin).append('.');
        } else {
            sb.append(" Aucun prix publié lisible : demande de prix à envoyer.");
        }
        return sb.toString();
    }

    private List<SerperClient.Hit> hits(java.util.function.Supplier<List<SerperClient.Hit>> call, List<String> errors) {
        try {
            return call.get();
        } catch (WebSearchException e) {
            errors.add(e.getMessage());
            return List.of();
        }
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
