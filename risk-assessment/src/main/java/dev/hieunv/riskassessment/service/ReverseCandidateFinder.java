package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.entity.WatchlistEntry;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReverseCandidateFinder {

    private static final String CANDIDATE_SQL = """
            SELECT cif
            FROM pcrt_customer_identity
            WHERE scan_target
              AND (    id_number_norm = :idNumber
                    OR (full_name_norm = :fullName AND dob        = :dob)
                    OR (full_name_norm = :fullName AND phone_norm = :phone)
                    OR (dob            = :dob      AND phone_norm = :phone)
                    %s )
            LIMIT :limit
            """;

    private static final String OLD_ID_BRANCH = "OR old_id_number_norm = :idNumber";

    private final NamedParameterJdbcTemplate jdbc;
    private final PcrtConfigService configService;

    public Candidates find(WatchlistEntry entry) {
        int max = configService.getInt("reverse.candidate.max", 20000);
        boolean matchOldId = configService.getBoolean("match.old.id.number", false);

        long startNanos = System.nanoTime();
        List<String> cifs = jdbc.queryForList(
                CANDIDATE_SQL.formatted(matchOldId ? OLD_ID_BRANCH : ""),
                params(entry).addValue("limit", max + 1),
                String.class);
        long micros = (System.nanoTime() - startNanos) / 1_000;

        boolean tooBroad = cifs.size() > max;
        if (tooBroad) {
            log.warn("Entry #{} matches more than {} customers — too broad for a reverse scan, forward scan required",
                    entry.getId(), max);
            return Candidates.builder().entryId(entry.getId()).cifs(List.of())
                    .tooBroad(true).elapsedMicros(micros).build();
        }
        return Candidates.builder().entryId(entry.getId()).cifs(cifs)
                .tooBroad(false).elapsedMicros(micros).build();
    }

    /**
     * Kế hoạch thực thi thật của câu trên — bằng chứng index có được dùng hay không.
     */
    public String explain(WatchlistEntry entry) {
        List<String> lines = jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) " + CANDIDATE_SQL.formatted(""),
                params(entry).addValue("limit", 20001),
                String.class);
        return String.join("\n", lines);
    }

    /**
     * Kiểu phải khai báo tường minh: khi giá trị là {@code null}, driver không suy ra được
     * kiểu của tham số và Postgres sẽ báo "could not determine data type of parameter".
     */
    private static MapSqlParameterSource params(WatchlistEntry entry) {
        return new MapSqlParameterSource()
                .addValue("idNumber", entry.getIdNumberNorm(), Types.VARCHAR)
                .addValue("fullName", entry.getFullNameNorm(), Types.VARCHAR)
                .addValue("phone", entry.getPhoneNorm(), Types.VARCHAR)
                .addValue("dob", entry.getDob() == null ? null : java.sql.Date.valueOf(entry.getDob()),
                        Types.DATE);
    }

    @Builder
    @Getter
    public static class Candidates {
        private final Long entryId;
        private final List<String> cifs;
        /**
         * true = bản ghi quá rộng, kết quả không dùng được, phải quét xuôi.
         */
        private final boolean tooBroad;
        private final long elapsedMicros;
    }
}
