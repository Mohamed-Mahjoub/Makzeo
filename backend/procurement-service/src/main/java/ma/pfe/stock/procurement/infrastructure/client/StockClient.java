package ma.pfe.stock.procurement.infrastructure.client;

import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.procurement.application.port.StockGateway;
import ma.pfe.stock.procurement.domain.model.StockInfo;
import ma.pfe.stock.procurement.infrastructure.config.ProcurementProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adaptateur HTTP vers stock-service : niveaux de stock et sorties sur une période. */
@Component
public class StockClient implements StockGateway {

    private static final String SERVICE = "stock-service";
    private static final int BATCH_SIZE = 500;
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 50;

    private final RestClient restClient;
    private final ZoneId zone;

    public StockClient(ProcurementProperties properties, ZoneId zone) {
        this.restClient = RestClientFactory.create(properties.stockServiceUrl());
        this.zone = zone;
    }

    @Override
    public Map<Long, StockInfo> stockSituation(Collection<Long> productIds, int lookbackDays) {
        Set<Long> wanted = new HashSet<>(productIds);
        Map<Long, Integer> quantities = new HashMap<>();
        List<Long> ids = new ArrayList<>(wanted);
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            List<RemoteResponses.StockLevel> levels = call(() -> restClient.get()
                    .uri(uri -> uri.path("/api/stock/levels/batch").queryParam("productIds", chunk.toArray()).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RemoteResponses.StockLevel>>() {
                    }));
            if (levels != null) {
                levels.forEach(l -> quantities.put(l.productId(), l.quantity() == null ? 0 : l.quantity()));
            }
        }

        // sorties de la période, agrégées par article
        LocalDate from = LocalDate.now(zone).minusDays(lookbackDays);
        Map<Long, Long> outflows = new HashMap<>();
        int page = 0;
        RemoteResponses.Page<RemoteResponses.Movement> current;
        do {
            int p = page;
            current = call(() -> restClient.get()
                    .uri(uri -> uri.path("/api/stock/movements").queryParam("type", "OUT")
                            .queryParam("from", from).queryParam("page", p).queryParam("size", PAGE_SIZE).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Movement>>() {
                    }));
            if (current == null || current.content() == null) {
                break;
            }
            for (RemoteResponses.Movement m : current.content()) {
                if (m.productId() != null && wanted.contains(m.productId()) && m.quantity() != null) {
                    outflows.merge(m.productId(), (long) m.quantity(), Long::sum);
                }
            }
            page++;
        } while (page < current.totalPages() && page < MAX_PAGES);

        double months = lookbackDays / 30.0;
        Map<Long, StockInfo> result = new HashMap<>();
        for (Long id : wanted) {
            result.put(id, new StockInfo(id, quantities.getOrDefault(id, 0), outflows.getOrDefault(id, 0L) / months));
        }
        return result;
    }

    private <T> T call(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RestClientException e) {
            throw new RemoteServiceException(SERVICE, "Service stock indisponible", e);
        }
    }
}
