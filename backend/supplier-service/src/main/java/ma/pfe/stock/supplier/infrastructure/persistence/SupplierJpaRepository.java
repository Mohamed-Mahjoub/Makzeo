package ma.pfe.stock.supplier.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierJpaRepository extends JpaRepository<SupplierEntity, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByCountryAndTaxId(String country, String taxId);

    boolean existsByCountryAndTaxIdAndIdNot(String country, String taxId, Long id);

    @Query("""
            select s from SupplierEntity s
            where (:active is null or s.active = :active)
              and (:country is null or s.country = :country)
              and (:keyword is null
                   or lower(s.name) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(s.code) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(coalesce(s.city, '')) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<SupplierEntity> search(@Param("keyword") String keyword, @Param("active") Boolean active,
                                @Param("country") String country, Pageable pageable);
}
