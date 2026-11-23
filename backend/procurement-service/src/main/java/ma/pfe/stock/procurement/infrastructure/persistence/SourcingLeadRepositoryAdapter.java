package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.SourcingLead;
import ma.pfe.stock.procurement.domain.port.SourcingLeadRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SourcingLeadRepositoryAdapter implements SourcingLeadRepository {

    private final SourcingLeadJpaRepository jpa;
    private final MarketSupplierJpaRepository suppliers;

    public SourcingLeadRepositoryAdapter(SourcingLeadJpaRepository jpa, MarketSupplierJpaRepository suppliers) {
        this.jpa = jpa;
        this.suppliers = suppliers;
    }

    @Override
    public SourcingLead save(SourcingLead lead) {
        return jpa.save(SourcingLeadEntity.from(lead)).toDomain(lead.supplierName());
    }

    @Override
    public List<SourcingLead> findByProduct(Long productId) {
        return withNames(jpa.findByProductIdOrderByDetectedAtDesc(productId));
    }

    @Override
    public List<SourcingLead> findRecent(int limit) {
        return withNames(jpa.findAllByOrderByDetectedAtDesc(PageRequest.of(0, limit)));
    }

    private List<SourcingLead> withNames(List<SourcingLeadEntity> leads) {
        Map<Long, String> names = suppliers.findAllById(leads.stream().map(SourcingLeadEntity::getMarketSupplierId)
                        .distinct().toList()).stream()
                .map(MarketSupplierEntity::toDomain)
                .collect(Collectors.toMap(s -> s.getId(), s -> s.getName(), (a, b) -> a));
        return leads.stream().map(l -> l.toDomain(names.getOrDefault(l.getMarketSupplierId(), "?"))).toList();
    }

}
