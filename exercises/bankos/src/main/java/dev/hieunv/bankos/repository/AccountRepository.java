package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.Account;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // READ_UNCOMMITTED — the dangerous one
    @Lock(LockModeType.NONE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    Optional<Account> findByIdUncommitted(@Param("id") Long id);

    // READ_COMMITTED — the safe default
    @Transactional(isolation = Isolation.READ_COMMITTED)
    Optional<Account> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}
