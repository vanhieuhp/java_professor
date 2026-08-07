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

/**
 * Chiều NGƯỢC của luật K1: cho một bản ghi danh sách, tìm những khách hàng khớp nó.
 *
 * <h2>Luật gốc viết ngược lại</h2>
 * Luật là: trùng <b>Số GTTT</b>, HOẶC trùng <b>≥2 trong 4</b> trường với cùng một bản ghi.
 * Vì bất kỳ cặp nào có chứa Số GTTT đã bị vế đầu bao trọn, chỉ còn 3 cặp thật sự từ
 * {họ tên, ngày sinh, SĐT}. Tổng cộng 4 nhánh, không hơn:
 * <pre>
 *   id_number_norm = :id
 *   full_name_norm = :name AND dob        = :dob
 *   full_name_norm = :name AND phone_norm = :phone
 *   dob            = :dob  AND phone_norm = :phone
 * </pre>
 *
 * <h2>Ràng buộc "cùng một bản ghi" biến mất</h2>
 * Ở chiều xuôi, đây là phần khó nhất — phải gom bằng chứng vào
 * {@code Map<entryId, EnumSet<MatchField>>} rồi mới đếm, nếu không sẽ có người trùng tên
 * với ông A và trùng ngày sinh với bà B rồi bị khóa ví oan. Ở chiều ngược, mỗi câu query
 * chỉ nói về đúng MỘT bản ghi, nên ràng buộc đó được thỏa mãn tự động. Cùng một luật,
 * đổi chiều nhìn thì độ khó chuyển sang chỗ khác.
 *
 * <h2>NULL tự lo phần của nó</h2>
 * Bản ghi không có SĐT thì {@code :phone} là NULL, và {@code phone_norm = NULL} cho ra NULL
 * chứ không phải TRUE — hai nhánh có SĐT tự tắt. Đó đúng là điều luật yêu cầu (không được
 * tính một trường mình không có), và nó khớp với hành vi của
 * {@code IdentityIndex.byPhone(null)} bên chiều xuôi. Không cần một dòng {@code if} nào.
 *
 * <h2>Đây chỉ là bước LỌC, không phải bước quyết định</h2>
 * Danh sách CIF trả về vẫn đi qua {@code IdentityMatcher} y như cũ. Nghĩa là nếu câu query
 * này trả về thừa thì chỉ tốn công vô ích, không sai kết quả. Nhưng nếu nó trả về
 * <b>thiếu</b> thì hệ thống bỏ sót — nên nó phải là tập bao, tuyệt đối không được cắt bớt.
 * Đó là lý do chạm trần thì báo {@code tooBroad} và bắt quay về quét xuôi, thay vì lấy
 * đại {@code max} dòng đầu.
 */
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

    /** Q3 — Số GTTT cũ. Tắt mặc định, giữ đồng bộ với {@code IdentityMatcher.matchOldIdNumber}. */
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

    /** Kế hoạch thực thi thật của câu trên — bằng chứng index có được dùng hay không. */
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
        /** true = bản ghi quá rộng, kết quả không dùng được, phải quét xuôi. */
        private final boolean tooBroad;
        private final long elapsedMicros;
    }
}
