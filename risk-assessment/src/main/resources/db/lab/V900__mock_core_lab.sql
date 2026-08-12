-- Core vi GIA — chi la do dung thu nghiem, khong thuoc schema cua PCRT.
-- KHONG nam tren duong Flyway mac dinh. Bat bang CA HAI:
--   spring.flyway.locations=classpath:db/migration,classpath:db/lab
--   pcrt.mock-core.enabled=true
-- Danh so V900 de khong bao gio va cham voi V3, V4... cua schema that.

CREATE SCHEMA core;

CREATE TABLE core.wallet_customer
(
    id              BIGSERIAL PRIMARY KEY,
    cif             VARCHAR(50)  NOT NULL UNIQUE,
    customer_type   VARCHAR(2)   NOT NULL,     -- I = individual, O = organization
    status          VARCHAR(20)  NOT NULL,     -- ACTIVE | APPROVED | LOCKED | CLOSED
    full_name       VARCHAR(255) NOT NULL,
    dob             DATE,
    phone           VARCHAR(30),
    id_number       VARCHAR(50),
    old_id_number   VARCHAR(50),
    country_code    VARCHAR(2),
    occupation_code VARCHAR(50),
    position_code   VARCHAR(50),
    risk_score      SMALLINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_core_type   CHECK (customer_type IN ('I', 'O')),
    CONSTRAINT ck_core_status CHECK (status IN ('ACTIVE', 'APPROVED', 'LOCKED', 'CLOSED'))
);

CREATE INDEX idx_core_scan_target
    ON core.wallet_customer (id)
    WHERE customer_type = 'I' AND status IN ('ACTIVE', 'APPROVED');

CREATE INDEX idx_core_update_time ON core.wallet_customer (update_time);
CREATE INDEX idx_core_created_at ON core.wallet_customer (created_at);

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

INSERT INTO core.wallet_customer (cif, customer_type, status, full_name, dob, phone,
                                  id_number, country_code, occupation_code, position_code, risk_score)
VALUES
    -- Blacklist hit by ID number
    ('CIF-BL-001', 'I', 'ACTIVE', 'Nguyễn Văn An', '1990-01-15', '0912111222', '001090111222', 'VN', NULL, NULL, NULL),
    -- Blacklist hit by 2 fields (name + date of birth) on the same record
    ('CIF-BL-002', 'I', 'APPROVED', 'nguyễn  văn ân', '1990-01-15', '0988000111', '038099000111', 'VN', NULL, NULL, NULL),
    -- Blacklist hit by name + phone in +84 form
    ('CIF-BL-003', 'I', 'ACTIVE', 'Trần Thị Bình', '2000-01-01', '+84987333444', '038099000222', 'VN', NULL, NULL, NULL),

    -- FALSE POSITIVE: shares a name with record #1 and a date of birth with record #2.
    -- Must NOT be flagged.
    ('CIF-FP-001', 'I', 'ACTIVE', 'Nguyễn Văn An', '1985-06-20', '0999888777', '038099000333', 'VN', NULL, NULL, NULL),

    -- Watchlist hits only (TH3), no blacklist hit
    ('CIF-WL-001', 'I', 'ACTIVE', 'Đỗ Thị Lan', '1988-07-19', '0966444555', '001188444555', 'VN', NULL, NULL, NULL),
    ('CIF-WL-002', 'I', 'ACTIVE', 'Lý Thu Hà', '1991-02-14', '0911222333', '038099000444', 'IR', NULL, NULL, NULL),
    ('CIF-WL-003', 'I', 'APPROVED', 'Ngô Bá Đạt', '1989-08-30', '0911222444', '038099000555', 'VN', 'CASINO_OPERATOR', NULL, NULL),
    ('CIF-WL-004', 'I', 'ACTIVE', 'Trịnh Văn Kiên', '1980-10-10', '0911222666', '038099000666', 'VN', NULL, 'DEPARTMENT_HEAD', NULL),

    -- MUST BE EXCLUDED at B1: status is neither Active nor Approved
    ('CIF-SKIP-LOCKED', 'I', 'LOCKED', 'Lê Hoàng Nam', '1978-11-02', '0905222111', '001078222111', 'VN', NULL, NULL, 7),
    -- MUST BE EXCLUDED at B1: organization, not an individual
    ('CIF-SKIP-ORG', 'O', 'ACTIVE', 'Lê Hoàng Nam', '1978-11-02', '0905222111', '001078222111', 'VN', NULL, NULL, NULL),
    -- MUST BE EXCLUDED at TH3b: already scored 7
    ('CIF-SKIP-SCORE7', 'I', 'ACTIVE', 'Phan Văn Bảy', '1979-07-07', '0977777777', '038099000777', 'VN', NULL, NULL, 7);


-- Background volume so keyset pagination has real work. Derived deterministically from g,
-- never random() — re-running produces the same data.
INSERT INTO core.wallet_customer (cif, customer_type, status, full_name, dob, phone,
                                  id_number, country_code, occupation_code, risk_score,
                                  created_at, update_time)
SELECT 'CIF' || lpad(g::text, 8, '0'),
       CASE WHEN g % 50 = 0 THEN 'O' ELSE 'I' END,
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
