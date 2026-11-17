package ma.pfe.stock.stockmgmt.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockItemJpaRepository extends JpaRepository<StockItemEntity, Long>,
        JpaSpecificationExecutor<StockItemEntity> {

    Optional<StockItemEntity> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItemEntity s where s.productId = :productId")
    Optional<StockItemEntity> lockByProductId(@Param("productId") Long productId);

    /** Création idempotente (sans erreur si une autre transaction a créé la ligne entre-temps). */
    @Modifying
    @Query(value = """
            insert into stock_items (product_id, product_reference, product_name, min_stock, quantity, version)
            values (:productId, :reference, :name, :minStock, 0, 0)
            on conflict (product_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("productId") Long productId, @Param("reference") String reference,
                       @Param("name") String name, @Param("minStock") int minStock);

    List<StockItemEntity> findByProductIdIn(Collection<Long> productIds);

    @Query("select coalesce(sum(s.quantity), 0) from StockItemEntity s")
    long sumQuantity();
}
