package ma.pfe.stock.procurement.domain.port;

import ma.pfe.stock.common.pagination.PageResult;
import ma.pfe.stock.procurement.domain.model.Rfq;
import ma.pfe.stock.procurement.domain.model.RfqStatus;

import java.util.Collection;
import java.util.Optional;

public interface RfqRepository {

    Rfq save(Rfq rfq);

    Optional<Rfq> findById(Long id);

    PageResult<Rfq> search(RfqStatus status, Long productId, int page, int size);

    /** Une demande encore ouverte existe-t-elle déjà pour ce couple article / fournisseur ? */
    boolean existsOpen(Long productId, Long marketSupplierId, Collection<RfqStatus> openStatuses);

    long countByStatus(RfqStatus status);
}
