package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.Cif;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CifRepository extends JpaRepository<Cif, Long> {

    Optional<Cif> findByCode(String code);

    boolean existsByCode(String code);
}