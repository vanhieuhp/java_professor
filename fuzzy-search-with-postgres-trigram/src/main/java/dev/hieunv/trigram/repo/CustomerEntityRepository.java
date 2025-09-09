package dev.hieunv.trigram.repo;

import dev.hieunv.trigram.entity.CustomerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CustomerEntityRepository extends CrudRepository<CustomerEntity, UUID>, JpaSpecificationExecutor<CustomerEntity> {

    @Query("""
          select c
          from CustomerEntity c
          where
              trgm_word_similarity(:search, c.contactDetails.firstName)
              OR trgm_word_similarity(:search, c.contactDetails.lastName)
              OR trgm_word_similarity(:search, c.address.city)
              OR trgm_word_similarity(:search, c.address.street)
            """)
    Page<CustomerEntity> findAll(@Param("search") String search, Pageable pageable);

    @Query(value = """
                    SELECT *
                    FROM customer
                    WHERE :search <% contact_first_name
                       or :search <% contact_last_name
                       or :search <%  address_city
                       or :search <% address_street
            """, nativeQuery = true)
    Page<CustomerEntity> findAllByNativeQuery(@Param("search") String search, Pageable pageable);
}