package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Modifying
    @Query("Update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("""
        update Product  p  set p.status = 'PENDING', p.sagaId = :sagaId
        where p.id = :id
        and p.status = 'AVAILABLE'
        and p.stock >= :qty
""")
    int claimSemanticLock(@Param("id") Long id,
                          @Param("sagaId") String sagaId,
                          @Param("qty") int qty);

    @Modifying
    @Query("""
        UPDATE Product p
        SET p.status = 'AVAILABLE', p.sagaId = null
        WHERE p.id = :id
        AND p.sagaId = :sagaId
        """)
    int releaseSemanticLock(@Param("id") Long id, @Param("sagaId") String sagaId);

    // Confirm sale — called when payment succeeds
    @Modifying
    @Query("""
        UPDATE Product p
        SET p.status = 'SOLD', p.stock = p.stock - :qty
        WHERE p.id = :id
        AND p.sagaId = :sagaId
        """)
    int confirmSale(@Param("id") Long id,
                    @Param("sagaId") String sagaId,
                    @Param("qty") int qty);
}
