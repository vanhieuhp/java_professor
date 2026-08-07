package dev.hieunv.riskassessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Tham số vận hành đọc từ DB, không hardcode.
 * <p>
 * Quan trọng nhất là {@code th3.scan.cron} — chính là "x giờ hàng ngày" của spec A.5-B1.
 * Spec nói rõ x nằm trong CSDL PCRT, nên nó không được là {@code @Scheduled(cron = "...")}
 * biên dịch cứng vào code: đổi giờ chạy phải là đổi một dòng dữ liệu, không phải build lại.
 */
@Entity
@Table(name = "pcrt_config")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PcrtConfig {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
