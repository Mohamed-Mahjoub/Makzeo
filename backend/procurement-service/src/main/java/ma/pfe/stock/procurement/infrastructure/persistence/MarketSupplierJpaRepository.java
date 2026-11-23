package ma.pfe.stock.procurement.infrastructure.persistence;

import ma.pfe.stock.procurement.domain.model.MarketSupplierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketSupplierJpaRepository extends JpaRepository<MarketSupplierEntity, Long> {

    Optional<MarketSupplierEntity> findByNormalizedKey(String normalizedKey);

    Optional<MarketSupplierEntity> findFirstByCatalogSupplierId(Long catalogSupplierId);

    Page<MarketSupplierEntity> findByStatus(MarketSupplierStatus status, Pageable pageable);

    long countByStatus(MarketSupplierStatus status);
}
