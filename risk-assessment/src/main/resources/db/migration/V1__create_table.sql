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
    CONSTRAINT ck_cat_risk_level CHECK (risk_level IN ('HIGH', 'MEDIUM')),
    CONSTRAINT ck_cat_score      CHECK (risk_score BETWEEN 2 AND 7),
    CONSTRAINT ck_cat_blacklist  CHECK (
        (is_blacklist AND priority = 0 AND risk_score = 7 AND match_type = 'K1')
            OR (NOT is_blacklist AND priority BETWEEN 1 AND 9 AND risk_score BETWEEN 2 AND 5)
        )
);

COMMENT ON COLUMN watchlist_category.sub_order IS
    'Thứ tự duyệt trong cùng priority — giải Q2 (A.8)';
COMMENT ON COLUMN watchlist_category.entries_changed_at IS
    'Lần cuối DS này có bản ghi thay đổi. Dùng cho: (1) trigger TH1, (2) TH3-B1 chọn nhánh 3a/3b, (3) invalidate cache — giải Q6';

ALTER TABLE watchlist_category
    ADD CONSTRAINT uq_cat_id_match_type UNIQUE (id, match_type);


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

    CONSTRAINT ck_entry_norm CHECK (
        (full_name IS NULL) = (full_name_norm IS NULL)
            AND (phone IS NULL) = (phone_norm IS NULL)
            AND (id_number IS NULL) = (id_number_norm IS NULL)
        )
);

CREATE INDEX idx_entry_by_category ON watchlist_entry (category_id) WHERE active;

COMMENT ON COLUMN watchlist_entry.dob_year IS
    'Dùng khi DS mẫu chỉ có năm sinh, không có ngày/tháng (Phase 2 mục 2.1)';
COMMENT ON COLUMN watchlist_entry.source_ref IS
    'Mã bản ghi ở nguồn gốc (UN/FATF/...) — cần khi giải trình với cơ quan thanh tra';
COMMENT ON COLUMN watchlist_entry.normalizer_version IS
    'Đổi quy tắc chuẩn hóa thì mọi dòng cũ sai → dùng cột này để backfill';


CREATE TABLE customer_scan_queue
(
    id                 BIGSERIAL PRIMARY KEY,
    scan_batch_id      UUID        NOT NULL,
    trigger_type       VARCHAR(4)  NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    cif                VARCHAR(50) NOT NULL,

    full_name          VARCHAR(320),
    full_name_norm     VARCHAR(320),
    dob                DATE,
    phone              VARCHAR(50),
    phone_norm         VARCHAR(15),
    id_number          VARCHAR(50),
    id_number_norm     VARCHAR(50),
    old_id_number      VARCHAR(50),
    old_id_number_norm VARCHAR(50),
    country_code       VARCHAR(2),
    occupation_code    VARCHAR(100),
    position_code      VARCHAR(100),

    core_risk_score    SMALLINT,
    enqueued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at       TIMESTAMPTZ,

    CONSTRAINT ck_queue_status  CHECK (status IN ('PENDING', 'PROCESSED')),
    CONSTRAINT ck_queue_trigger CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_queue_done    CHECK ((status = 'PROCESSED') = (processed_at IS NOT NULL))
);

CREATE INDEX idx_queue_pending
    ON customer_scan_queue (scan_batch_id, id)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX uq_queue_batch_cif
    ON customer_scan_queue (scan_batch_id, cif);

CREATE INDEX idx_queue_cif ON customer_scan_queue (cif, enqueued_at DESC);

COMMENT ON COLUMN customer_scan_queue.old_id_number IS
    'Số GTTT cũ (CMND 9 số trước khi đổi CCCD). Optional ở TH2 — có dùng để so khớp DS đen hay không: Q3';
COMMENT ON COLUMN customer_scan_queue.occupation_code IS
    'Nghề nghiệp. Core thật trả về CHỮ TỰ DO chứ không phải mã, nên tiêu chí K3 chỉ khớp được khi có bảng ánh xạ chữ → mã. Chưa có bảng đó.';
COMMENT ON COLUMN customer_scan_queue.position_code IS
    'Chức vụ. Cùng vấn đề chữ-tự-do như occupation_code.';
COMMENT ON COLUMN customer_scan_queue.core_risk_score IS
    'KHÔNG CÒN ĐƯỢC ĐIỀN: Core thật không có cột điểm rủi ro nào. Điều kiện "điểm khác 7" của TH3b nay lọc bằng customer_risk_result của chính PCRT.';


CREATE TABLE pcrt_config
(
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description  VARCHAR(500),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);


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

    CONSTRAINT ck_batch_trigger CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_batch_status  CHECK (status IN ('ENQUEUING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_batch_unfinished
    ON scan_batch (started_at)
    WHERE status IN ('ENQUEUING', 'PROCESSING');


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
    attempt_count     SMALLINT     NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    last_error        VARCHAR(500),

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_result_trigger CHECK (trigger_type IN ('T1', 'T1R', 'T2', 'T3A', 'T3B')),
    CONSTRAINT ck_result_level   CHECK (risk_level IN ('HIGH', 'MEDIUM')),
    CONSTRAINT ck_result_send    CHECK (core_send_status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_result_sent_at CHECK ((core_send_status = 'SENT') = (core_sent_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_result_latest_per_cif
    ON customer_risk_result (cif)
    WHERE is_latest;

CREATE INDEX idx_result_dispatch
    ON customer_risk_result (next_attempt_at NULLS FIRST, id)
    WHERE core_send_status IN ('PENDING', 'FAILED');

CREATE INDEX idx_result_batch ON customer_risk_result (scan_batch_id);
CREATE INDEX idx_result_cif ON customer_risk_result (cif, created_at DESC);

COMMENT ON COLUMN customer_risk_result.next_attempt_at IS
    'Thời điểm sớm nhất được thử gửi lại. Backoff giữa các LẦN CHẠY JOB — khác với retry trong một lần gọi.';


CREATE TABLE pcrt_customer_identity
(
    cif                VARCHAR(50) PRIMARY KEY,
    scan_target        BOOLEAN     NOT NULL,

    -- 4 trường định danh của luật K1, CHỈ ở dạng đã chuẩn hóa.
    full_name_norm     VARCHAR(320),
    dob                DATE,
    phone_norm         VARCHAR(15),
    id_number_norm     VARCHAR(50),
    old_id_number_norm VARCHAR(50),

    normalizer_version SMALLINT    NOT NULL DEFAULT 1,
    core_updated_at    TIMESTAMPTZ NOT NULL,
    synced_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at         TIMESTAMPTZ
);

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

CREATE INDEX idx_pci_core_updated ON pcrt_customer_identity (core_updated_at);

COMMENT ON COLUMN pcrt_customer_identity.core_updated_at IS
    'Mốc thứ tự, KHÔNG phải mốc kiểm toán. Ghi đè chỉ xảy ra khi mốc mới >= mốc đang có, nên một sự kiện TH2 tới muộn không thể đè lên dữ liệu mới hơn.';
COMMENT ON COLUMN pcrt_customer_identity.deleted_at IS
    'Bia mộ. NOT NULL = Core báo KH này không còn. Luôn đi kèm scan_target = false, nên 5 partial index (đều có vị từ scan_target) tự động loại dòng này ra.';


CREATE TABLE pcrt_core_event_inbox
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type    VARCHAR(24)   NOT NULL,
    event_id      VARCHAR(64)   NOT NULL,
    cif           VARCHAR(50)   NOT NULL,
    change_type   VARCHAR(20)   NOT NULL,
    source        VARCHAR(16)   NOT NULL,
    payload       JSONB         NOT NULL,
    received_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at  TIMESTAMPTZ,
    process_error VARCHAR(1000),

    CONSTRAINT uq_core_inbox_event  UNIQUE (event_type, event_id),
    CONSTRAINT ck_core_inbox_type   CHECK (event_type = 'CUSTOMER_CHANGED'),
    CONSTRAINT ck_core_inbox_change CHECK (change_type IN ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'DELETED')),
    CONSTRAINT ck_core_inbox_source CHECK (source IN ('KAFKA', 'REST'))
);

CREATE INDEX idx_core_inbox_unprocessed ON pcrt_core_event_inbox (id) WHERE processed_at IS NULL;
CREATE INDEX idx_core_inbox_cif ON pcrt_core_event_inbox (cif, received_at DESC);