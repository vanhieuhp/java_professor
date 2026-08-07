package dev.hieunv.riskassessment.mockcore;

import dev.hieunv.riskassessment.event.ChangeType;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.UUID;

/**
 * PHÍA CORE VÍ — thực sự đổi dữ liệu khách hàng rồi phát sự kiện.
 * <p>
 * Tồn tại để đầu kia của tích hợp có thật. Không có nó thì "PCRT nghe sự kiện" chỉ được chứng
 * minh bằng những gói tin do chính PCRT tự bịa ra, và mọi giả định về việc Core gửi cái gì,
 * lúc nào, theo mốc thời gian nào đều không được kiểm chứng.
 *
 * <h2>{@code occurredAt} lấy từ đồng hồ DATABASE</h2>
 * Sự kiện mang chính giá trị {@code update_time} mà câu UPDATE vừa ghi, không phải
 * {@code Instant.now()} của JVM. Bản chiếu bên PCRT so mốc này với mốc nó đang giữ — vốn cũng
 * bắt nguồn từ {@code update_time} — nên hai giá trị phải nằm trên cùng một đồng hồ. Lệch vài
 * trăm mili-giây giữa hai máy chủ là đủ để chốt thứ tự chặn nhầm một sự kiện hợp lệ, hoặc cho
 * qua một sự kiện đáng lẽ phải chặn. Đây là loại lỗi rất khó nhìn ra vì nó chỉ xảy ra khi hai
 * thay đổi sát nhau.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pcrt.mock-core.enabled", havingValue = "true", matchIfMissing = true)
public class MockCoreCustomerChanger {

    private static final String UPDATE = """
            UPDATE core.wallet_customer SET
                full_name       = COALESCE(?, full_name),
                dob             = COALESCE(?, dob),
                phone           = COALESCE(?, phone),
                id_number       = COALESCE(?, id_number),
                old_id_number   = COALESCE(?, old_id_number),
                country_code    = COALESCE(?, country_code),
                occupation_code = COALESCE(?, occupation_code),
                position_code   = COALESCE(?, position_code),
                status          = COALESCE(?, status),
                update_time     = now()
            WHERE cif = ?
            RETURNING id, cif, customer_type, status, full_name, dob, phone, id_number,
                      old_id_number, country_code, occupation_code, position_code, update_time
            """;

    private static final String SELECT_FOR_DELETE = """
            DELETE FROM core.wallet_customer WHERE cif = ?
            RETURNING id, cif, customer_type, status, full_name, dob, phone, id_number,
                      old_id_number, country_code, occupation_code, position_code, now() AS update_time
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MockCoreEventPublisher publisher;

    /**
     * Đổi dữ liệu và phát sự kiện.
     * <p>
     * {@code @Transactional} chỉ bao câu UPDATE — {@code KafkaTemplate.send} là lời gọi mạng,
     * nằm ngoài tầm với của transaction database. Đây chính là dual-write đã nói ở
     * {@link MockCoreEventPublisher}: DB commit rồi mà publish hỏng thì PCRT không bao giờ
     * biết. Để nguyên và ghi rõ, thay vì che bằng một khối try/catch trông như đã xử lý.
     */
    @Transactional
    public CustomerChangedEvent change(String cif, ChangeType changeType, MockCoreChangeRequest req) {
        CustomerChangedEvent event = changeType == ChangeType.DELETED
                ? deleteAndBuild(cif)
                : updateAndBuild(cif, req);
        event.setChangeType(changeType);
        event.setEventId(UUID.randomUUID().toString());

        publisher.publish(event);
        return event;
    }

    private CustomerChangedEvent updateAndBuild(String cif, MockCoreChangeRequest r) {
        MockCoreChangeRequest req = r == null ? MockCoreChangeRequest.builder().build() : r;
        try {
            return jdbcTemplate.queryForObject(UPDATE,
                    (rs, n) -> toEvent(rs),
                    req.getFullName(),
                    req.getDob() == null ? null : Date.valueOf(req.getDob()),
                    req.getPhone(),
                    req.getIdNumber(),
                    req.getOldIdNumber(),
                    req.getCountryCode(),
                    req.getOccupationCode(),
                    req.getPositionCode(),
                    req.getStatus(),
                    cif);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("CIF không tồn tại bên Core: " + cif);
        }
    }

    private CustomerChangedEvent deleteAndBuild(String cif) {
        try {
            return jdbcTemplate.queryForObject(SELECT_FOR_DELETE, (rs, n) -> toEvent(rs), cif);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("CIF không tồn tại bên Core: " + cif);
        }
    }

    private static CustomerChangedEvent toEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        Date dob = rs.getDate("dob");
        return CustomerChangedEvent.builder()
                .coreId(rs.getLong("id"))
                .cif(rs.getString("cif"))
                .customerType(rs.getString("customer_type"))
                .status(rs.getString("status"))
                .fullName(rs.getString("full_name"))
                .dob(dob == null ? null : dob.toLocalDate())
                .phone(rs.getString("phone"))
                .idNumber(rs.getString("id_number"))
                .oldIdNumber(rs.getString("old_id_number"))
                .countryCode(rs.getString("country_code"))
                .occupationCode(rs.getString("occupation_code"))
                .positionCode(rs.getString("position_code"))
                .occurredAt(rs.getTimestamp("update_time").toInstant())
                .build();
    }
}
