INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('th3.scan.cron', '0 0 2 * * *',
        'Cron of the daily periodic assessment — the "x hours" in A.5-B1. Changing the value '
            || 'reschedules the job, no restart needed.'),
       ('batch.page.size', '1000',
        'Rows per page when reading core, and per batch when pulling work off the queue.'),
       ('th1.auto.trigger.enabled', 'true',
        'Fire TH1 automatically when the blacklist is detected to have changed.'),
       ('batch.resume.on.startup', 'true',
        'On startup, continue scans left unfinished (answers Q9).'),

       ('core.base-url', 'http://localhost:8080/mock-core',
        'Wallet core address. Pointing at the real system is a value change, not a rebuild.'),
       ('core.dispatch.enabled', 'false',
        'Master switch of the job that pushes results to core. Seeded OFF: the mock core is no '
            || 'longer part of this schema, and the real core does not expose a risk-update '
            || 'endpoint yet, so /mock-core would answer 404 — a 4xx is permanent, which would '
            || 'park every result as FAILED. Turn on together with core.base-url.'),
       ('core.dispatch.interval-ms', '5000',
        'How often the dispatch job runs.'),
       ('core.dispatch.batch-size', '200',
        'Results sent per job run.'),
       ('core.dispatch.max-attempts', '5',
        'Attempts before a result is parked as exhausted and needs a human.'),
       ('core.dispatch.backoff-base-seconds', '10',
        'Base of the exponential backoff between dispatch attempts.'),
       ('core.circuit-breaker.failure-threshold', '5',
        'Consecutive failures before the breaker opens and stops calling core.'),
       ('core.circuit-breaker.open-seconds', '30',
        'How long the breaker stays open before letting one probe through.'),
       ('core.event.rest-fallback.enabled', 'true',
        'Accept core change events over REST as well as Kafka, so a broker outage does not stop '
            || 'change notifications entirely.'),

       ('identity.sync.page.size', '2000',
        'Rows per page when syncing the core customer table into pcrt_customer_identity.'),
       ('identity.sync.watermark', '1970-01-01T00:00:00Z',
        'Delta sync watermark of the identity mirror. Only the sync job writes it, always with the '
            || 'CORE database clock read at the START of the run. NOTE: the real core has no '
            || '"last modified" column, so the delta path only catches NEWLY ENROLLED customers; '
            || 'profile edits arrive via TH2 events or the next FULL sync.'),
       ('reverse.candidate.max', '20000',
        'Ceiling on candidates for ONE watchlist record. Hitting it means the record is too broad '
            || 'and that record must fall back to a forward scan. Truncating is not an option: '
            || 'truncating means missing someone.'),

       ('core.customer.type.individual', 'I',
        'The "individual customer" code in core. The customer table holds only I and O.'),
       ('core.customer.status.scan-target', '*',
        'Comma-separated customer_status codes in the scan set. "*" = every status. Core stores '
            || 'customer_status as a single character (A, C, L, P, X observed) with no lookup '
            || 'table, so nobody can yet say which one means "wallet alive". The default leans to '
            || 'over-scanning: scanning someone outside the set costs CPU, excluding someone '
            || 'inside it is a miss nobody detects. PENDING BA CONFIRMATION.'),
       ('core.person.role.identity', 'INITIAL_USER',
        'The customer_person_role role used to reach an identity document in the person table. '
            || 'Only 578 of 28,960 individual customers have this link, so ~98% reach PCRT with an '
            || 'empty ID number. PENDING BA CONFIRMATION on where individual ID numbers live.');


-- ---------------------------------------------------------------------
-- Seed: the blacklist plus the 15 watchlists of table A.6.
-- Q2 (A.8): within one priority, sub_order decides evaluation order. "More severe first"
-- is provisional and still to be confirmed with BA.
-- ---------------------------------------------------------------------
INSERT INTO watchlist_category (code, name, priority, sub_order, match_type, risk_level, risk_score, reason,
                                is_blacklist)
VALUES ('BLACKLIST', 'Blacklist', 0, 1, 'K1', 'HIGH', 7, 'Blacklist match', TRUE),

       ('WARNING_CUSTOMER', 'Flagged customers', 1, 1, 'K1', 'HIGH', 5, 'Flagged customer match', FALSE),
       ('PEP', 'Politically exposed persons', 1, 2, 'K1', 'HIGH', 5, 'Politically exposed person match', FALSE),

       ('UN_SANCTION_COUNTRY', 'UN sanctioned countries', 2, 1, 'K2', 'HIGH', 5,
        'Resident of a UN sanctioned country', FALSE),

       ('FRAUD_SUSPECT', 'Customers suspected of fraud or criminal offences', 3, 1, 'K1', 'HIGH', 4,
        'Suspected of fraud or a criminal offence', FALSE),
       ('CRIMINAL_DEFENDANT', 'Customers indicted, on trial, or convicted', 3, 2, 'K1', 'HIGH', 4,
        'Indicted, on trial, or convicted', FALSE),

       ('FATF_HIGH_RISK_COUNTRY', 'High-risk countries published by FATF', 4, 1, 'K2', 'HIGH', 4,
        'FATF high-risk country', FALSE),
       ('FINCEN_COUNTRY', 'Top money-laundering risk countries per FinCEN (US)', 4, 2, 'K2', 'HIGH', 4,
        'FinCEN money-laundering risk country', FALSE),
       ('EU_TAX_HAVEN_COUNTRY', 'Tax haven countries (EU blacklist)', 4, 3, 'K2', 'HIGH', 4,
        'Tax haven country (EU blacklist)', FALSE),

       ('HIGH_RISK_OCCUPATION', 'High-risk occupations', 5, 1, 'K3', 'HIGH', 4, 'High-risk occupation', FALSE),

       ('HIGH_RISK_POSITION', 'High-risk positions', 6, 1, 'K4', 'HIGH', 4, 'High-risk position', FALSE),
       ('MEDIUM_RISK_POSITION', 'Medium-risk positions', 6, 2, 'K4', 'MEDIUM', 3, 'Medium-risk position', FALSE),

       ('STR_REPORTED', 'Customers with a suspicious transaction report', 7, 1, 'K1', 'MEDIUM', 3,
        'Subject of a suspicious transaction report', FALSE),
       ('EPAY_WATCH', 'Other customers under review (Epay internal)', 7, 2, 'K1', 'MEDIUM', 3,
        'Under internal Epay review', FALSE),

       ('MEDIUM_RISK_OCCUPATION', 'Medium-risk occupations', 8, 1, 'K3', 'MEDIUM', 3,
        'Medium-risk occupation', FALSE),

       ('OTHER_WATCH_COUNTRY', 'Other monitored countries', 9, 1, 'K2', 'MEDIUM', 2,
        'Resident of another monitored country', FALSE);


-- ---------------------------------------------------------------------
-- Seed: sample watchlist records. People and codes are fictional; the names stay
-- Vietnamese because they are DATA the normalizer is tested against, not labels.
--
-- Two cases planted on purpose:
--   * "Đỗ Thị Lan" is in BOTH priority 3 (score 4) and priority 7 (score 3) — early-stop
--     must return 4, not 3, and not 4+3.
--   * 'IR' is in BOTH priority 2 (score 5) and priority 4 (score 4) — priority 2 must win.
-- ---------------------------------------------------------------------

-- K1 — định danh cá nhân
INSERT INTO watchlist_entry (category_id, match_type, full_name, full_name_norm, dob,
                             phone, phone_norm, id_number, id_number_norm, source, source_ref)
SELECT c.id,
       'K1',
       v.full_name,
       v.full_name_norm,
       v.dob::date,
       v.phone,
       v.phone_norm,
       v.id_number,
       v.id_number_norm,
       v.source,
       v.source_ref
FROM watchlist_category c
         JOIN (VALUES
                   ('BLACKLIST', 'Nguyễn Văn An', 'NGUYEN VAN AN', '1990-01-15',
                    '0912111222', '0912111222', '001090111222', '001090111222', 'UN_SC', 'UNSC-0001'),
                   ('BLACKLIST', 'Trần Thị Bình', 'TRAN THI BINH', '1985-06-20',
                    '0987333444', '0987333444', '001185333444', '001185333444', 'UN_SC', 'UNSC-0002'),
                   ('BLACKLIST', 'Lê Hoàng Nam', 'LE HOANG NAM', '1978-11-02',
                    '0905222111', '0905222111', '001078222111', '001078222111', 'SBV', 'SBV-0007'),

                   ('WARNING_CUSTOMER', 'Phạm Minh Tuấn', 'PHAM MINH TUAN', '1992-03-08',
                    '0978111333', '0978111333', '001092111333', '001092111333', 'EPAY_INTERNAL', 'WARN-0031'),
                   ('PEP', 'Vũ Đình Khánh', 'VU DINH KHANH', '1970-05-10',
                    '0913555666', '0913555666', '001070555666', '001070555666', 'PEP_VN', 'PEP-0114'),

                   ('FRAUD_SUSPECT', 'Đỗ Thị Lan', 'DO THI LAN', '1988-07-19',
                    '0966444555', '0966444555', '001188444555', '001188444555', 'C03', 'C03-2024-118'),
                   ('CRIMINAL_DEFENDANT', 'Bùi Văn Sơn', 'BUI VAN SON', '1983-09-25',
                    '0944777888', '0944777888', '001083777888', '001083777888', 'TAND', 'TAND-2023-441'),

                   -- Same person as FRAUD_SUSPECT above
                   ('STR_REPORTED', 'Đỗ Thị Lan', 'DO THI LAN', '1988-07-19',
                    '0966444555', '0966444555', '001188444555', '001188444555', 'AMLD', 'STR-2024-9902'),
                   ('EPAY_WATCH', 'Hoàng Thị Mai', 'HOANG THI MAI', '1995-12-01',
                    '0933888999', '0933888999', '001195888999', '001195888999', 'EPAY_INTERNAL', 'WATCH-0450')
) AS v(code, full_name, full_name_norm, dob, phone, phone_norm, id_number, id_number_norm, source, source_ref)
              ON c.code = v.code;


-- K2 — quốc gia
INSERT INTO watchlist_entry (category_id, match_type, country_code, source, source_ref)
SELECT c.id, 'K2', v.country_code, v.source, v.source_ref
FROM watchlist_category c
         JOIN (VALUES ('UN_SANCTION_COUNTRY', 'IR', 'UN_SC', 'UNSC-CTRY-IR'),
                      ('UN_SANCTION_COUNTRY', 'KP', 'UN_SC', 'UNSC-CTRY-KP'),
                      ('UN_SANCTION_COUNTRY', 'SY', 'UN_SC', 'UNSC-CTRY-SY'),

                      -- IR repeats on purpose: one country on two lists of different priority
                      ('FATF_HIGH_RISK_COUNTRY', 'MM', 'FATF', 'FATF-BL-MM'),
                      ('FATF_HIGH_RISK_COUNTRY', 'IR', 'FATF', 'FATF-BL-IR'),

                      ('FINCEN_COUNTRY', 'RU', 'FINCEN', 'FINCEN-RU'),
                      ('FINCEN_COUNTRY', 'AF', 'FINCEN', 'FINCEN-AF'),

                      ('EU_TAX_HAVEN_COUNTRY', 'PA', 'EU', 'EU-TH-PA'),
                      ('EU_TAX_HAVEN_COUNTRY', 'VU', 'EU', 'EU-TH-VU'),
                      ('EU_TAX_HAVEN_COUNTRY', 'FJ', 'EU', 'EU-TH-FJ'),

                      ('OTHER_WATCH_COUNTRY', 'KH', 'EPAY_INTERNAL', 'WATCH-CTRY-KH'),
                      ('OTHER_WATCH_COUNTRY', 'LA', 'EPAY_INTERNAL', 'WATCH-CTRY-LA')
) AS v(code, country_code, source, source_ref)
              ON c.code = v.code;


-- K3 — nghề nghiệp
INSERT INTO watchlist_entry (category_id, match_type, occupation_code, source, source_ref)
SELECT c.id, 'K3', v.occupation_code, v.source, v.source_ref
FROM watchlist_category c
         JOIN (VALUES ('HIGH_RISK_OCCUPATION', 'CASINO_OPERATOR', 'EPAY_INTERNAL', 'OCC-H-01'),
                      ('HIGH_RISK_OCCUPATION', 'CRYPTO_TRADER', 'EPAY_INTERNAL', 'OCC-H-02'),
                      ('HIGH_RISK_OCCUPATION', 'ARMS_DEALER', 'EPAY_INTERNAL', 'OCC-H-03'),

                      ('MEDIUM_RISK_OCCUPATION', 'REAL_ESTATE_BROKER', 'EPAY_INTERNAL', 'OCC-M-01'),
                      ('MEDIUM_RISK_OCCUPATION', 'JEWELRY_DEALER', 'EPAY_INTERNAL', 'OCC-M-02')
) AS v(code, occupation_code, source, source_ref)
              ON c.code = v.code;


-- K4 — chức vụ
INSERT INTO watchlist_entry (category_id, match_type, position_code, source, source_ref)
SELECT c.id, 'K4', v.position_code, v.source, v.source_ref
FROM watchlist_category c
         JOIN (VALUES ('HIGH_RISK_POSITION', 'SHELL_COMPANY_CEO', 'EPAY_INTERNAL', 'POS-H-01'),
                      ('HIGH_RISK_POSITION', 'OFFSHORE_BOARD_MEMBER', 'EPAY_INTERNAL', 'POS-H-02'),

                      ('MEDIUM_RISK_POSITION', 'DEPARTMENT_HEAD', 'EPAY_INTERNAL', 'POS-M-01')
) AS v(code, position_code, source, source_ref)
              ON c.code = v.code;

UPDATE watchlist_category
SET entries_changed_at = now();