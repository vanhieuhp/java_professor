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
import java.time.LocalDate;

/**
 * Bản chiếu định danh của PCRT — 4 trường của luật K1, đã chuẩn hóa, do PCRT sở hữu.
 *
 * <h2>Vì sao PCRT giữ bản sao thay vì query thẳng Core</h2>
 * Không phải để đi nhanh hơn, mà để <b>index được</b>. Luật so khớp chạy trên giá trị đã
 * chuẩn hóa, còn Core lưu chữ thô. Muốn Core index được giá trị chuẩn hóa thì Core phải
 * mang cột chuẩn hóa của PCRT — và mỗi lần PCRT sửa quy tắc chuẩn hóa lại là một migration
 * trên bảng production của đội khác. Giữ bản sao ở PCRT thì ranh giới sở hữu sạch: dữ liệu
 * gốc là của Core, cách hiểu dữ liệu đó là của PCRT.
 *
 * <h2>Cái giá phải trả</h2>
 * Bản sao thì lệch được. Ba lớp bảo vệ:
 * <ul>
 *   <li>TH2 (KH đổi thông tin) đẩy thay đổi sang đây ngay — đường nhanh;</li>
 *   <li>job đồng bộ delta theo {@code update_time} — lưới hứng cho sự kiện bị mất;</li>
 *   <li>quét XUÔI toàn bộ định kỳ — cách duy nhất phát hiện bản sao đã lệch, vì nó
 *       đọc thẳng từ Core chứ không tin bảng này.</li>
 * </ul>
 * Lớp thứ ba là lý do quét xuôi không bị bỏ đi: nó đổi vai từ "đường chạy chính"
 * thành "phép đối chứng".
 */
@Entity
@Table(name = "pcrt_customer_identity")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerIdentity {

    @Id
    @Column(name = "cif", nullable = false, length = 50)
    private String cif;

    /**
     * KH cá nhân + Active/Approved. Nằm trong vị từ của cả 5 partial index.
     */
    @Column(name = "scan_target", nullable = false)
    private boolean scanTarget;

    @Column(name = "full_name_norm")
    private String fullNameNorm;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "phone_norm", length = 15)
    private String phoneNorm;

    @Column(name = "id_number_norm", length = 50)
    private String idNumberNorm;

    @Column(name = "old_id_number_norm", length = 50)
    private String oldIdNumberNorm;

    @Column(name = "normalizer_version", nullable = false)
    @Builder.Default
    private Short normalizerVersion = 1;

    @Column(name = "core_updated_at", nullable = false)
    private Instant coreUpdatedAt;

    /**
     * Ghi được qua JPA (bỏ {@code insertable = false}) vì đường TH2 nay dùng entity chứ không
     * còn dựa vào {@code DEFAULT now()} của cột. Người ghi qua JPA phải tự set — để trống là
     * vi phạm NOT NULL ngay lúc flush, chứ không âm thầm lệch.
     */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    /**
     * Bia mộ. Có dấu thời gian nghĩa là Core đã báo xóa; đường upsert xóa dấu này đi để một
     * CIF được tạo lại quay về tập quét thay vì nằm chết trong bản chiếu.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
