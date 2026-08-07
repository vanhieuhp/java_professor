-- =====================================================================
-- PCRT — Phase 1 schema
-- Tham chiếu spec: docs/risk_assetment.md mục A.6, A.7, A.3-B2, A.4-B2, A.5-B2
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. watchlist_category — 15 DS mẫu (A.6) + DS đen (priority = 0)
-- ---------------------------------------------------------------------
CREATE TABLE watchlist_category
(
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(50)  NOT NULL,
    name               VARCHAR(255) NOT NULL,
    priority           SMALLINT     NOT NULL,
    sub_order          SMALLINT     NOT NULL DEFAULT 1,
    match_type         VARCHAR(2)   NOT NULL,
    risk_level         VARCHAR(20)  NOT NULL,
    risk_score         SMALLINT     NOT NULL,
    reason             VARCHAR(255) NOT NULL,
    is_blacklist       BOOLEAN      NOT NULL DEFAULT FALSE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    entries_changed_at TIMESTAMPTZ,

    CONSTRAINT uq_cat_code       UNIQUE (code),
    CONSTRAINT uq_cat_order      UNIQUE (priority, sub_order),
    CONSTRAINT ck_cat_match_type CHECK (match_type IN ('K1', 'K2', 'K3', 'K4')),
    CONSTRAINT ck_cat_risk_level CHECK (risk_level IN ('CAO', 'TRUNG_BINH')),
    CONSTRAINT ck_cat_score      CHECK (risk_score BETWEEN 2 AND 7),
    -- DS đen bắt buộc: priority 0, điểm 7, kiểu K1.
    -- DS mẫu bắt buộc priority 1..9 và điểm 2..5 — điểm 7 là ĐỘC QUYỀN của DS đen,
    -- vì chỉ điểm 7 mới kéo theo quy trình khóa CIF.
    CONSTRAINT ck_cat_blacklist  CHECK (
        (is_blacklist AND priority = 0 AND risk_score = 7 AND match_type = 'K1')
            OR (NOT is_blacklist AND priority BETWEEN 1 AND 9 AND risk_score BETWEEN 2 AND 5)
        )
);

COMMENT ON COLUMN watchlist_category.sub_order IS
    'Thứ tự duyệt trong cùng priority — giải Q2 (A.8)';
COMMENT ON COLUMN watchlist_category.entries_changed_at IS
    'Lần cuối DS này có bản ghi thay đổi. Dùng cho: (1) trigger TH1, (2) TH3-B1 chọn nhánh 3a/3b, (3) invalidate cache — giải Q6';

-- Cho phép FK ghép (category_id, match_type) ở bảng dưới, đảm bảo entry
-- không bao giờ lệch match_type so với category của nó.
ALTER TABLE watchlist_category
    ADD CONSTRAINT uq_cat_id_match_type UNIQUE (id, match_type);


-- ---------------------------------------------------------------------
-- 2. watchlist_entry — bản ghi của MỌI danh sách (1 bảng chung, cột nullable)
--    Hàng rào bù lại cho tính linh hoạt: ck_entry_shape + ck_entry_norm
-- ---------------------------------------------------------------------
CREATE TABLE watchlist_entry
(
    id                 BIGSERIAL PRIMARY KEY,
    category_id        BIGINT      NOT NULL,
    match_type         VARCHAR(2)  NOT NULL,

    -- K1 — định danh cá nhân
    full_name          VARCHAR(255),
    full_name_norm     VARCHAR(255),
    dob                DATE,
    dob_year           SMALLINT,
    phone              VARCHAR(30),
    phone_norm         VARCHAR(15),
    id_number          VARCHAR(50),
    id_number_norm     VARCHAR(50),

    -- K2 / K3 / K4 — thuộc tính đơn
    country_code       VARCHAR(2),
    occupation_code    VARCHAR(50),
    position_code      VARCHAR(50),

    source             VARCHAR(100),
    source_ref         VARCHAR(100),
    normalizer_version SMALLINT    NOT NULL DEFAULT 1,
    active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_entry_category FOREIGN KEY (category_id, match_type)
        REFERENCES watchlist_category (id, match_type),

    -- Mỗi match_type chỉ được dùng đúng nhóm cột của nó.
    CONSTRAINT ck_entry_shape CHECK (
        CASE match_type
            WHEN 'K1' THEN country_code IS NULL AND occupation_code IS NULL AND position_code IS NULL
                AND (id_number_norm IS NOT NULL OR full_name_norm IS NOT NULL)
            WHEN 'K2' THEN country_code IS NOT NULL
                AND full_name IS NULL AND id_number IS NULL AND phone IS NULL
                AND dob IS NULL AND dob_year IS NULL
                AND occupation_code IS NULL AND position_code IS NULL
            WHEN 'K3' THEN occupation_code IS NOT NULL
                AND full_name IS NULL AND id_number IS NULL AND phone IS NULL
                AND dob IS NULL AND dob_year IS NULL
                AND country_code IS NULL AND position_code IS NULL
            WHEN 'K4' THEN position_code IS NOT NULL
                AND full_name IS NULL AND id_number IS NULL AND phone IS NULL
                AND dob IS NULL AND dob_year IS NULL
                AND country_code IS NULL AND occupation_code IS NULL
            ELSE FALSE
            END
        ),

    -- Có giá trị thô thì BẮT BUỘC có giá trị đã chuẩn hóa.
    -- Thiếu cột _norm = bản ghi không bao giờ match = false negative âm thầm.
    CONSTRAINT ck_entry_norm CHECK (
        (full_name IS NULL) = (full_name_norm IS NULL)
            AND (phone IS NULL) = (phone_norm IS NULL)
            AND (id_number IS NULL) = (id_number_norm IS NULL)
        )
);

-- Đường nạp dữ liệu duy nhất của matching engine: lấy toàn bộ entry của 1 category
-- rồi dựng index trong RAM. Không cần index theo từng trường.
CREATE INDEX idx_entry_by_category ON watchlist_entry (category_id) WHERE active;

COMMENT ON COLUMN watchlist_entry.dob_year IS
    'Dùng khi DS mẫu chỉ có năm sinh, không có ngày/tháng (Phase 2 mục 2.1)';
COMMENT ON COLUMN watchlist_entry.source_ref IS
    'Mã bản ghi ở nguồn gốc (UN/FATF/...) — cần khi giải trình với cơ quan thanh tra';
COMMENT ON COLUMN watchlist_entry.normalizer_version IS
    'Đổi quy tắc chuẩn hóa thì mọi dòng cũ sai → dùng cột này để backfill';


-- ---------------------------------------------------------------------
-- 3. customer_scan_queue — DS KH cần đánh giá
--    A.3-B2 (TH1), A.4-B2 (TH2), A.5-B2 (TH3)
-- ---------------------------------------------------------------------
CREATE TABLE customer_scan_queue
(
    id                 BIGSERIAL PRIMARY KEY,
    scan_batch_id      UUID        NOT NULL,
    trigger_type       VARCHAR(4)  NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'CXL',

    cif                VARCHAR(50) NOT NULL,

    -- SNAPSHOT dữ liệu KH tại thời điểm quét — đủ cho CẢ 4 kiểu tiêu chí (A.7).
    -- B3 tuyệt đối không gọi lại Core.
    full_name          VARCHAR(255),
    full_name_norm     VARCHAR(255),
    dob                DATE,
    phone              VARCHAR(30),
    phone_norm         VARCHAR(15),
    id_number          VARCHAR(50),
    id_number_norm     VARCHAR(50),
    old_id_number      VARCHAR(50),
    old_id_number_norm VARCHAR(50),
    country_code       VARCHAR(2),
    occupation_code    VARCHAR(50),
    position_code      VARCHAR(50),

    core_risk_score    SMALLINT,
    enqueued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at       TIMESTAMPTZ,

    CONSTRAINT ck_queue_status  CHECK (status IN ('CXL', 'DA_XU_LY')),
    CONSTRAINT ck_queue_trigger CHECK (trigger_type IN ('T1', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_queue_done    CHECK ((status = 'DA_XU_LY') = (processed_at IS NOT NULL))
);

-- Đường nóng B3/B5: lấy việc tiếp theo.
-- Partial index — dòng chuyển sang DA_XU_LY tự động rời khỏi index,
-- nên index luôn chỉ lớn bằng đúng lượng việc còn lại.
CREATE INDEX idx_queue_pending
    ON customer_scan_queue (scan_batch_id, id)
    WHERE status = 'CXL';

-- Chạy lại B1 giữa chừng không sinh trùng việc trong cùng 1 batch (liên quan Q9).
CREATE UNIQUE INDEX uq_queue_batch_cif
    ON customer_scan_queue (scan_batch_id, cif);

-- Tra cứu lịch sử quét của 1 khách hàng.
CREATE INDEX idx_queue_cif ON customer_scan_queue (cif, enqueued_at DESC);

COMMENT ON COLUMN customer_scan_queue.old_id_number IS
    'Số GTTT cũ (CMND 9 số trước khi đổi CCCD). Optional ở TH2 — có dùng để so khớp DS đen hay không: Q3';
COMMENT ON COLUMN customer_scan_queue.core_risk_score IS
    'Điểm rủi ro hiện có bên Core, phục vụ điều kiện lọc "điểm <> 7" của TH3b — Q4';
