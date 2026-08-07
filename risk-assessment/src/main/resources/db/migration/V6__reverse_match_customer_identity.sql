-- =====================================================================
-- V6 — Quét NGƯỢC: từ bản ghi danh sách tìm ra khách hàng
--
-- Chiều xuôi (V1-V5): đọc 5 triệu KH, mỗi người tra vào index băm của danh sách.
-- Chi phí O(số KH) — không phụ thuộc danh sách thay đổi nhiều hay ít.
--
-- Chiều ngược: với mỗi bản ghi VỪA THAY ĐỔI, hỏi DB "ai khớp bản ghi này".
-- Chi phí O(số bản ghi đổi × log(số KH)) — thêm 1 dòng vào DS đen thì tốn đúng 1 query.
--
-- Cản trở duy nhất khiến chiều ngược không làm thẳng trên Core được: core.wallet_customer
-- lưu chữ THÔ ("Đỗ Thị Lan"), còn luật so khớp chạy trên chữ ĐÃ CHUẨN HÓA ("DO THI LAN").
-- WHERE pcrt_normalize(full_name) = '...' là seq scan 5 triệu dòng — mỗi bản ghi một lần.
-- Muốn index được thì Core phải mang cột chuẩn hóa của PCRT, tức là logic nghiệp vụ của
-- PCRT rò rỉ vào schema của đội khác, và mỗi lần PCRT đổi quy tắc chuẩn hóa là một
-- migration + backfill trên bảng production của người ta.
--
-- Nên bảng chiếu này thuộc về PCRT. PCRT chuẩn hóa, PCRT index, PCRT đổi luật một mình.
-- =====================================================================

CREATE TABLE pcrt_customer_identity
(
    cif                VARCHAR(50) PRIMARY KEY,

    -- id bên Core: dùng làm con trỏ keyset khi đồng bộ, và để phát hiện dòng bị xóa.
    core_id            BIGINT      NOT NULL,

    -- "KH cá nhân, trạng thái Active/Approved" — đúng tập mà TH1/TH3a quét.
    -- Lưu thành cột thay vì suy ra lúc query, để nó vào được vị từ của partial index bên dưới.
    scan_target        BOOLEAN     NOT NULL,

    -- 4 trường định danh của luật K1, CHỈ ở dạng đã chuẩn hóa.
    -- Không lưu bản thô: bản thô là của Core, PCRT không phải là nơi giữ chân lý về nó.
    full_name_norm     VARCHAR(255),
    dob                DATE,
    phone_norm         VARCHAR(15),
    id_number_norm     VARCHAR(50),
    old_id_number_norm VARCHAR(50),

    -- Đổi quy tắc chuẩn hóa thì mọi dòng có version cũ là sai → cột này chỉ ra cần backfill dòng nào.
    normalizer_version SMALLINT    NOT NULL DEFAULT 1,

    -- update_time bên Core tại lần đồng bộ gần nhất — mốc để lấy delta lần sau.
    core_updated_at    TIMESTAMPTZ NOT NULL,
    synced_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_pci_core_id ON pcrt_customer_identity (core_id);

-- Con trỏ delta: "các dòng Core đổi sau mốc X".
CREATE INDEX idx_pci_core_updated ON pcrt_customer_identity (core_updated_at);

-- ---------------------------------------------------------------------
-- 4 index cho 4 trường. Đều là PARTIAL trên (scan_target AND cột IS NOT NULL).
--
-- Vì sao partial: NULL không bao giờ khớp trong luật này (bản ghi không có SĐT thì
-- không được tính trường SĐT), nên dòng NULL nằm trong index là dung lượng chết.
-- Và KH đã khóa/đóng ví thì không thuộc tập quét — cũng là dung lượng chết.
--
-- Vì sao KHÔNG dùng index ghép (full_name_norm, dob) cho từng cặp:
-- luật có 3 cặp → 3 index ghép, trong khi Postgres đã BitmapAnd được hai index đơn.
-- Mỗi trường đơn đã đủ chọn lọc (một họ tên khớp vài chục dòng trong 5 triệu), nên
-- index đơn quét ra ít dòng rồi lọc nốt là xong. Chỉ dựng index ghép khi EXPLAIN
-- chứng minh là cần — chưa đo thì chưa được thêm.
-- ---------------------------------------------------------------------
CREATE INDEX idx_pci_id_number ON pcrt_customer_identity (id_number_norm)
    WHERE scan_target AND id_number_norm IS NOT NULL;

CREATE INDEX idx_pci_old_id_number ON pcrt_customer_identity (old_id_number_norm)
    WHERE scan_target AND old_id_number_norm IS NOT NULL;

CREATE INDEX idx_pci_full_name ON pcrt_customer_identity (full_name_norm)
    WHERE scan_target AND full_name_norm IS NOT NULL;

CREATE INDEX idx_pci_dob ON pcrt_customer_identity (dob)
    WHERE scan_target AND dob IS NOT NULL;

CREATE INDEX idx_pci_phone ON pcrt_customer_identity (phone_norm)
    WHERE scan_target AND phone_norm IS NOT NULL;


-- =====================================================================
-- T1R — biến thể quét ngược của TH1. Cùng nghiệp vụ với T1, khác đường nạp hàng đợi.
-- Tách mã riêng để trong scan_batch / customer_risk_result nhìn ra ngay lần quét nào
-- đi đường nào — nếu dùng chung 'T1' thì không đối chiếu được hai chiều với nhau.
-- =====================================================================
ALTER TABLE scan_batch DROP CONSTRAINT ck_batch_trigger;
ALTER TABLE scan_batch ADD CONSTRAINT ck_batch_trigger
    CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B'));

ALTER TABLE customer_scan_queue DROP CONSTRAINT ck_queue_trigger;
ALTER TABLE customer_scan_queue ADD CONSTRAINT ck_queue_trigger
    CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B'));

ALTER TABLE customer_risk_result DROP CONSTRAINT ck_result_trigger;
ALTER TABLE customer_risk_result ADD CONSTRAINT ck_result_trigger
    CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B'));


INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('identity.sync.page.size', '2000',
        'Số dòng mỗi trang khi đồng bộ core.wallet_customer sang pcrt_customer_identity.'),
       ('reverse.candidate.max', '20000',
        'Trần số ứng viên cho MỘT bản ghi danh sách. Chạm trần nghĩa là bản ghi đó quá rộng '
            || '(tên + ngày sinh quá phổ biến) — khi đó phải quay về quét xuôi cho bản ghi đó, '
            || 'KHÔNG được cắt bớt: cắt bớt là bỏ sót, mà bỏ sót trong AML là sai nghiêm trọng nhất.');
