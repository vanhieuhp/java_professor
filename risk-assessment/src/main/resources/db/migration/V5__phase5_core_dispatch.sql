-- =====================================================================
-- Phase 5 — Tích hợp PCRT → Core ví
--
-- Nguyên tắc nền: ghi kết quả vào DB PCRT TRƯỚC, gửi Core SAU và NGOÀI transaction.
-- Nếu gộp lời gọi mạng vào trong transaction, một lần Core treo sẽ giữ khóa DB suốt
-- thời gian timeout; và nếu transaction rollback sau khi Core đã nhận thì hai bên
-- lệch nhau vĩnh viễn — Core đã khóa ví mà PCRT không có bản ghi nào giải thích.
-- =====================================================================

-- --- Trạng thái gửi Core: thêm số lần thử, thời điểm thử lại, lỗi gần nhất ---
ALTER TABLE customer_risk_result
    ADD COLUMN attempt_count   SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error      VARCHAR(500);

COMMENT ON COLUMN customer_risk_result.next_attempt_at IS
    'Thời điểm sớm nhất được thử gửi lại. Backoff giữa các LẦN CHẠY JOB — khác với retry trong một lần gọi.';

-- Hàng đợi gửi Core. Lại là partial index trên trạng thái đang hoạt động:
-- dòng đã SENT rời khỏi index, nên index luôn chỉ lớn bằng lượng việc còn lại.
DROP INDEX IF EXISTS idx_result_pending_core;
CREATE INDEX idx_result_dispatch
    ON customer_risk_result (next_attempt_at NULLS FIRST, id)
    WHERE core_send_status IN ('PENDING', 'FAILED');


-- =====================================================================
-- Sổ nhận lệnh của Core ví (thuộc phía Core, không phải PCRT)
--
-- ĐÂY LÀ CƠ CHẾ IDEMPOTENCY. PCRT gửi lại sau timeout là chuyện bình thường: nó không
-- phân biệt được "Core chưa nhận" với "Core đã nhận nhưng phản hồi bị mất". Nếu Core xử lý
-- cả hai lần, quy trình khóa CIF chạy hai lượt.
--
-- Khóa chống trùng phải do BÊN NHẬN giữ, và phải là ràng buộc UNIQUE trong DB chứ không
-- phải một câu "SELECT rồi IF" — hai request song song sẽ cùng vượt qua câu kiểm tra đó.
-- =====================================================================
CREATE TABLE core.risk_update_log
(
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    cif             VARCHAR(50)  NOT NULL,
    risk_level      VARCHAR(20)  NOT NULL,
    risk_score      SMALLINT     NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    cif_locked      BOOLEAN      NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_core_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_core_log_cif ON core.risk_update_log (cif, received_at DESC);


-- =====================================================================
-- Cấu hình
-- =====================================================================
INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('core.base-url', 'http://localhost:8080/mock-core',
        'Địa chỉ Core ví. Trỏ sang hệ thống thật chỉ là đổi giá trị này, không cần build lại.'),
       ('core.dispatch.interval-ms', '5000',
        'Chu kỳ job gửi kết quả sang Core.'),
       ('core.dispatch.batch-size', '200',
        'Số kết quả gửi mỗi lần chạy job.'),
       ('core.dispatch.max-attempts', '5',
        'Quá số lần này thì ngừng tự gửi, chờ can thiệp thủ công.'),
       ('core.dispatch.backoff-base-seconds', '10',
        'Backoff giữa các LẦN CHẠY JOB: base * 2^(attempt-1).'),
       ('core.circuit-breaker.failure-threshold', '5',
        'Số lần lỗi liên tiếp thì mở cầu dao, ngừng gọi Core.'),
       ('core.circuit-breaker.open-seconds', '30',
        'Thời gian giữ cầu dao mở trước khi thử lại một lần.');
