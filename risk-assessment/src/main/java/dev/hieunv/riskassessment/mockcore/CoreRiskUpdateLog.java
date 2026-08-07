package dev.hieunv.riskassessment.mockcore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Sổ nhận lệnh của Core ví — thuộc phía Core, không phải PCRT.
 * <p>
 * Đây là nơi idempotency thực sự được thực thi: cột {@code idempotency_key} có ràng buộc
 * UNIQUE, nên hai lệnh cùng khóa không thể cùng được ghi nhận, kể cả khi chúng tới đồng thời
 * trên hai kết nối khác nhau. Kiểm tra bằng "SELECT rồi IF" trong code sẽ thua ở đúng tình
 * huống đó — cả hai request đều thấy chưa có và cả hai đều xử lý.
 */
@Entity
@Table(name = "risk_update_log", schema = "core")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CoreRiskUpdateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "cif", nullable = false, length = 50)
    private String cif;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel;

    @Column(name = "risk_score", nullable = false)
    private Short riskScore;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "cif_locked", nullable = false)
    private boolean cifLocked;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;
}
