package ma.pfe.stock.reporting.infrastructure.client;

import ma.pfe.stock.common.client.RestClientFactory;
import ma.pfe.stock.common.exception.RemoteServiceException;
import ma.pfe.stock.common.exception.ResourceNotFoundException;
import ma.pfe.stock.reporting.application.port.StockGateway;
import ma.pfe.stock.reporting.domain.model.AlertData;
import ma.pfe.stock.reporting.domain.model.InventoryData;
import ma.pfe.stock.reporting.domain.model.MovementData;
import ma.pfe.stock.reporting.domain.model.StockLevelData;
import ma.pfe.stock.reporting.infrastructure.config.ReportingProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Adaptateur HTTP vers stock-service (niveaux, mouvements, alertes, inventaires). */
@Component
public class StockServiceClient implements StockGateway {

    private static final String SERVICE = "stock-service";
    private static final int BATCH_SIZE = 500;
    private static final int PAGE_SIZE = 1000;

    private final RestClient restClient;
    private final int maxRows;

    public StockServiceClient(ReportingProperties properties) {
        this.restClient = RestClientFactory.create(properties.stockServiceUrl());
        this.maxRows = properties.maxRows();
    }

    @Override
    public List<StockLevelData> findLevels(Collection<Long> productIds) {
        List<Long> ids = new ArrayList<>(productIds);
        List<StockLevelData> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            List<Long> chunk = ids.subList(i, Math.min(i + BATCH_SIZE, ids.size()));
            List<RemoteResponses.StockLevel> levels = call(() -> restClient.get()
                    .uri(uri -> uri.path("/api/stock/levels/batch").queryParam("productIds", chunk.toArray()).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RemoteResponses.StockLevel>>() {
                    }));
            if (levels != null) {
                levels.forEach(l -> result.add(new StockLevelData(l.productId(),
                        l.quantity() == null ? 0 : l.quantity(), l.status())));
            }
        }
        return result;
    }

    @Override
    public List<MovementData> findMovements(LocalDate from, LocalDate to, String type, Long productId) {
        return fetchAll(page -> uri -> uri.path("/api/stock/movements")
                        .queryParam("page", page).queryParam("size", PAGE_SIZE)
                        .queryParamIfPresent("from", Optional.ofNullable(from))
                        .queryParamIfPresent("to", Optional.ofNullable(to))
                        .queryParamIfPresent("type", Optional.ofNullable(type))
                        .queryParamIfPresent("productId", Optional.ofNullable(productId))
                        .build(),
                new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Movement>>() {
                },
                m -> new MovementData(m.id(), m.productId(), m.productReference(), m.productName(), m.type(), m.typeLabel(),
                        m.quantity(), m.signedQuantity(), m.quantityBefore(), m.quantityAfter(),
                        m.documentReference(), m.reason(), m.performedBy(), m.occurredAt()));
    }

    @Override
    public List<AlertData> findAlerts(boolean activeOnly) {
        return fetchAll(page -> uri -> uri.path("/api/stock/alerts")
                        .queryParam("page", page).queryParam("size", PAGE_SIZE)
                        .queryParam("activeOnly", activeOnly)
                        .build(),
                new ParameterizedTypeReference<RemoteResponses.Page<RemoteResponses.Alert>>() {
                },
                a -> new AlertData(a.id(), a.productReference(), a.productName(), a.type(), a.typeLabel(),
                        a.status(), a.quantity(), a.threshold(), a.createdAt()));
    }

    @Override
    public InventoryData findInventory(Long inventoryId) {
        RemoteResponses.Inventory inv;
        try {
            inv = restClient.get().uri("/api/stock/inventories/{id}", inventoryId).retrieve()
                    .body(RemoteResponses.Inventory.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Inventaire", inventoryId);
            }
            throw new RemoteServiceException(SERVICE, "Erreur stock-service (HTTP " + e.getStatusCode().value()
                    + ")", e);
        } catch (RestClientException e) {
            throw new RemoteServiceException(SERVICE, "Service stock indisponible", e);
        }
        if (inv == null) {
            throw new ResourceNotFoundException("Inventaire", inventoryId);
        }
        List<InventoryData.Line> lines = inv.lines() == null ? List.of() : inv.lines().stream()
                .map(l -> new InventoryData.Line(l.productReference(), l.productName(), l.expectedQuantity(),
                        l.countedQuantity(), l.difference(), l.countedBy()))
                .toList();
        return new InventoryData(inv.id(), inv.name(), inv.status(), inv.createdBy(), inv.createdAt(),
                inv.closedBy(), inv.closedAt(), lines);
    }

    /** Parcourt toutes les pages d'une ressource paginée (dans la limite de maxRows). */
    private <R, T> List<T> fetchAll(Function<Integer, Function<UriBuilder, URI>> uriForPage,
                                    ParameterizedTypeReference<RemoteResponses.Page<R>> type,
                                    Function<R, T> mapper) {
        List<T> result = new ArrayList<>();
        int page = 0;
        RemoteResponses.Page<R> current;
        do {
            Function<UriBuilder, URI> uri = uriForPage.apply(page);
            current = call(() -> restClient.get().uri(uri).retrieve().body(type));
            if (current == null || current.content() == null) {
                break;
            }
            for (R item : current.content()) {
                if (result.size() >= maxRows) {
                    return result;
                }
                result.add(mapper.apply(item));
            }
            page++;
        } while (page < current.totalPages());
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
