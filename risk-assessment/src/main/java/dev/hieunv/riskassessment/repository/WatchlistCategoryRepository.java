package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.entity.WatchlistCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WatchlistCategoryRepository extends JpaRepository<WatchlistCategory, Long> {

    @Query("""
            SELECT c
            FROM WatchlistCategory c
            WHERE c.blacklist = true
            AND c.active = true
            """)
    Optional<WatchlistCategory> findBlacklistCategory();

    @Query(value = """
            SELECT c FROM WatchlistCategory c
            WHERE c.blacklist = false AND c.active = true
            ORDER BY c.priority ASC, c.subOrder ASC
            """)
    List<WatchlistCategory> findCifWatchlist();

    boolean existsByBlacklistFalseAndActiveTrueAndEntriesChangedAtBetween(Instant from, Instant to);

    @Query(value = "SELECT max(entries_changed_at) FROM watchlist_category WHERE active", nativeQuery = true)
    Instant findLatestEntriesChangedAt();

    @Modifying
    @Query(value = "UPDATE watchlist_category SET entries_changed_at = now() WHERE code = :code", nativeQuery = true)
    int touchByCode(@Param("code") String code);
}
