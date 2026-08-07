-- =====================================================================
-- Phase 3 — Batch flow TH1 + TH3
--
-- Gồm 4 phần:
--   1. schema "core"  — MÔ PHỎNG DB Core ví. Thực tế đây là database khác,
--                       PCRT chỉ đọc. Tách schema để ranh giới đó vẫn rõ.
--   2. pcrt_config    — giờ chạy job định kỳ "x giờ" (A.5-B1) và các tham số vận hành
--   3. scan_batch     — mỗi lần quét là một bản ghi, để quan sát được tiến độ
--   4. customer_risk_result — kết quả rủi ro (chốt Q5: APPEND lịch sử + cờ is_latest)
-- =====================================================================


-- =====================================================================
-- 1. MÔ PHỎNG DB CORE VÍ
-- =====================================================================
CREATE SCHEMA core;

CREATE TABLE core.wallet_customer
(
    id              BIGSERIAL PRIMARY KEY,
    cif             VARCHAR(50)  NOT NULL UNIQUE,
    customer_type   VARCHAR(2)   NOT NULL,     -- CN = cá nhân, TC = tổ chức
    status          VARCHAR(20)  NOT NULL,     -- ACTIVE | APPROVED | LOCKED | CLOSED
    full_name       VARCHAR(255) NOT NULL,
    dob             DATE,
    phone           VARCHAR(30),
    id_number       VARCHAR(50),
    old_id_number   VARCHAR(50),
    country_code    VARCHAR(2),
    occupation_code VARCHAR(50),
    position_code   VARCHAR(50),
    risk_score      SMALLINT,                  -- điểm rủi ro Core đang giữ — lọc "điểm <> 7" của TH3b
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_core_type   CHECK (customer_type IN ('CN', 'TC')),
    CONSTRAINT ck_core_status CHECK (status IN ('ACTIVE', 'APPROVED', 'LOCKED', 'CLOSED'))
);

-- Partial index cho đúng tập KH mà TH1/TH3a quét: cá nhân, Active/Approved.
-- Keyset pagination đi theo id nên id phải là cột dẫn đầu.
CREATE INDEX idx_core_scan_target
    ON core.wallet_customer (id)
    WHERE customer_type = 'CN' AND status IN ('ACTIVE', 'APPROVED');

-- TH3b lọc theo ngày phát sinh: created_at T-1 HOẶC update_time T-1.
CREATE INDEX idx_core_update_time ON core.wallet_customer (update_time);
CREATE INDEX idx_core_created_at ON core.wallet_customer (created_at);


-- --- Các khách hàng được cài sẵn có chủ ý, để quan sát từng nhánh logic ---
INSERT INTO core.wallet_customer (cif, customer_type, status, full_name, dob, phone,
                                  id_number, country_code, occupation_code, position_code, risk_score)
VALUES
    -- Trùng DS đen bằng số GTTT
    ('CIF-BL-001', 'CN', 'ACTIVE', 'Nguyễn Văn An', '1990-01-15', '0912111222', '001090111222', 'VN', NULL, NULL, NULL),
    -- Trùng DS đen bằng 2 trường (tên + ngày sinh) cùng một bản ghi
    ('CIF-BL-002', 'CN', 'APPROVED', 'nguyễn  văn ân', '1990-01-15', '0988000111', '038099000111', 'VN', NULL, NULL, NULL),
    -- Trùng DS đen bằng tên + SĐT dạng +84
    ('CIF-BL-003', 'CN', 'ACTIVE', 'Trần Thị Bình', '2000-01-01', '+84987333444', '038099000222', 'VN', NULL, NULL, NULL),

    -- FALSE POSITIVE: trùng tên với bản ghi #1, trùng ngày sinh với bản ghi #2.
    -- Phải KHÔNG bị đánh dấu rủi ro.
    ('CIF-FP-001', 'CN', 'ACTIVE', 'Nguyễn Văn An', '1985-06-20', '0999888777', '038099000333', 'VN', NULL, NULL, NULL),

    -- Chỉ trùng DS mẫu (TH3), không trùng DS đen
    ('CIF-WL-001', 'CN', 'ACTIVE', 'Đỗ Thị Lan', '1988-07-19', '0966444555', '001188444555', 'VN', NULL, NULL, NULL),
    ('CIF-WL-002', 'CN', 'ACTIVE', 'Lý Thu Hà', '1991-02-14', '0911222333', '038099000444', 'IR', NULL, NULL, NULL),
    ('CIF-WL-003', 'CN', 'APPROVED', 'Ngô Bá Đạt', '1989-08-30', '0911222444', '038099000555', 'VN', 'CASINO_OPERATOR', NULL, NULL),
    ('CIF-WL-004', 'CN', 'ACTIVE', 'Trịnh Văn Kiên', '1980-10-10', '0911222666', '038099000666', 'VN', NULL, 'DEPARTMENT_HEAD', NULL),

    -- PHẢI BỊ LOẠI ở bước B1: trạng thái không phải Active/Approved
    ('CIF-SKIP-LOCKED', 'CN', 'LOCKED', 'Lê Hoàng Nam', '1978-11-02', '0905222111', '001078222111', 'VN', NULL, NULL, 7),
    -- PHẢI BỊ LOẠI ở bước B1: khách hàng tổ chức, không phải cá nhân
    ('CIF-SKIP-TC', 'TC', 'ACTIVE', 'Lê Hoàng Nam', '1978-11-02', '0905222111', '001078222111', 'VN', NULL, NULL, NULL),
    -- PHẢI BỊ LOẠI ở TH3b: đã có điểm 7
    ('CIF-SKIP-SCORE7', 'CN', 'ACTIVE', 'Phan Văn Bảy', '1979-07-07', '0977777777', '038099000777', 'VN', NULL, NULL, 7);


-- --- Khối lượng nền: 50.000 khách hàng để keyset pagination có việc thật ---
-- Dữ liệu suy ra tất định từ g, không dùng random() — chạy lại luôn ra kết quả như nhau.
INSERT INTO core.wallet_customer (cif, customer_type, status, full_name, dob, phone,
                                  id_number, country_code, occupation_code, risk_score,
                                  created_at, update_time)
SELECT 'CIF' || lpad(g::text, 8, '0'),
       CASE WHEN g % 50 = 0 THEN 'TC' ELSE 'CN' END,
       CASE WHEN g % 37 = 0 THEN 'LOCKED' WHEN g % 2 = 0 THEN 'ACTIVE' ELSE 'APPROVED' END,
       'Khach Hang So ' || g,
       DATE '1960-01-01' + (g % 18000),
       '09' || lpad((g % 100000000)::text, 8, '0'),
       lpad(g::text, 12, '0'),
       CASE WHEN g % 5000 = 0 THEN 'IR' WHEN g % 997 = 0 THEN 'KH' ELSE 'VN' END,
       CASE WHEN g % 1231 = 0 THEN 'CRYPTO_TRADER' ELSE 'OFFICE_WORKER' END,
       NULL,
       now() - ((g % 5) || ' days')::interval,
       now() - ((g % 5) || ' days')::interval
FROM generate_series(1, 50000) g;


-- =====================================================================
-- 2. pcrt_config — tham số vận hành, đọc từ DB chứ không hardcode
-- =====================================================================
CREATE TABLE pcrt_config
(
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description  VARCHAR(500),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('th3.scan.cron', '0 0 2 * * *',
        'Giờ chạy đánh giá định kỳ hàng ngày — chính là "x giờ" ở A.5-B1. Đổi giá trị này là job đổi lịch, không cần restart.'),
       ('batch.page.size', '1000',
        'Số bản ghi mỗi lần đọc Core / mỗi lần lấy việc khỏi hàng đợi.'),
       ('th1.auto.trigger.enabled', 'true',
        'Tự kích hoạt TH1 khi phát hiện DS đen được điều chỉnh.'),
       ('batch.resume.on.startup', 'true',
        'Khi khởi động, tiếp tục các lần quét còn dở (giải Q9).');


-- =====================================================================
-- 3. scan_batch — mỗi lần quét một bản ghi
-- =====================================================================
CREATE TABLE scan_batch
(
    id              UUID PRIMARY KEY,
    trigger_type    VARCHAR(4)  NOT NULL,
    status          VARCHAR(20) NOT NULL,
    enqueued_count  INTEGER     NOT NULL DEFAULT 0,
    processed_count INTEGER     NOT NULL DEFAULT 0,
    matched_count   INTEGER     NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ,
    note            VARCHAR(500),

    CONSTRAINT ck_batch_trigger CHECK (trigger_type IN ('T1', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_batch_status  CHECK (status IN ('ENQUEUING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

-- Q9 — resume: tìm nhanh các lần quét chưa xong.
CREATE INDEX idx_batch_unfinished
    ON scan_batch (started_at)
    WHERE status IN ('ENQUEUING', 'PROCESSING');


-- =====================================================================
-- 4. customer_risk_result — kết quả đánh giá
--
-- CHỐT Q5: APPEND lịch sử, không ghi đè. Lý do: đây là hệ thống bị thanh tra —
-- phải trả lời được "ngày X hệ thống đánh giá khách hàng này thế nào", nên bản ghi
-- cũ không được biến mất. Bản mới nhất đánh dấu bằng cờ is_latest, và unique index
-- partial đảm bảo mỗi CIF chỉ có đúng MỘT bản ghi is_latest.
-- (Vẫn phải xác nhận lại với BA — đây là giả định, không phải yêu cầu đã có.)
-- =====================================================================
CREATE TABLE customer_risk_result
(
    id                BIGSERIAL PRIMARY KEY,
    cif               VARCHAR(50)  NOT NULL,
    scan_batch_id     UUID         NOT NULL,
    scan_queue_id     BIGINT       NOT NULL,
    trigger_type      VARCHAR(4)   NOT NULL,

    risk_level        VARCHAR(20)  NOT NULL,
    risk_score        SMALLINT     NOT NULL,
    reason            VARCHAR(255) NOT NULL,

    category_code     VARCHAR(50)  NOT NULL,
    entry_id          BIGINT,
    matched_fields    VARCHAR(200),
    lock_cif_required BOOLEAN      NOT NULL DEFAULT FALSE,

    is_latest         BOOLEAN      NOT NULL DEFAULT TRUE,
    core_send_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    core_sent_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_result_trigger CHECK (trigger_type IN ('T1', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_result_level   CHECK (risk_level IN ('CAO', 'TRUNG_BINH')),
    CONSTRAINT ck_result_send    CHECK (core_send_status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_result_sent_at CHECK ((core_send_status = 'SENT') = (core_sent_at IS NOT NULL))
);

-- Mỗi CIF chỉ có đúng một bản ghi "mới nhất" — DB tự bảo đảm, không phụ thuộc code.
CREATE UNIQUE INDEX uq_result_latest_per_cif
    ON customer_risk_result (cif)
    WHERE is_latest;

-- Hàng đợi gửi Core (Phase 5) — lại là partial index trên trạng thái đang hoạt động.
CREATE INDEX idx_result_pending_core
    ON customer_risk_result (id)
    WHERE core_send_status = 'PENDING';

CREATE INDEX idx_result_batch ON customer_risk_result (scan_batch_id);
CREATE INDEX idx_result_cif ON customer_risk_result (cif, created_at DESC);
