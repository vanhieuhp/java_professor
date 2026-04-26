package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FunctionRepository extends JpaRepository<Function, Long> {

    Optional<Function> findByCode(String code);
}