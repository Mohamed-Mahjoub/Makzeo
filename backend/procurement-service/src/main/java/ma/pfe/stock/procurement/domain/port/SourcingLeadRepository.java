package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.procurement.domain.model.SourcingLead;

import java.util.List;

public interface SourcingLeadRepository {

    SourcingLead save(SourcingLead lead);

    List<SourcingLead> findByProduct(Long productId);

    List<SourcingLead> findRecent(int limit);
}
