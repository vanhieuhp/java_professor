-- =====================================================================
-- Dữ liệu mẫu để chạy thử. Người và mã đều là hư cấu.
--
-- Vài trường hợp được cài sẵn có chủ ý:
--   * "Đỗ Thị Lan" nằm trong CẢ ưu tiên 3 (nghi ngờ gian lận, điểm 4)
--     và ưu tiên 7 (báo cáo GD đáng ngờ, điểm 3) → chứng minh early-stop
--     phải trả về điểm 4, không phải điểm 3, và không phải 4+3.
--   * Mã 'IR' nằm trong CẢ ưu tiên 2 (LHQ, điểm 5) và ưu tiên 4 (FATF, điểm 4)
--     → ưu tiên 2 phải thắng.
-- =====================================================================

-- ------------------------- K1: định danh cá nhân -------------------------
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
                   -- DS đen
                   ('BLACKLIST', 'Nguyễn Văn An', 'NGUYEN VAN AN', '1990-01-15',
                    '0912111222', '0912111222', '001090111222', '001090111222', 'UN_SC', 'UNSC-0001'),
                   ('BLACKLIST', 'Trần Thị Bình', 'TRAN THI BINH', '1985-06-20',
                    '0987333444', '0987333444', '001185333444', '001185333444', 'UN_SC', 'UNSC-0002'),
                   ('BLACKLIST', 'Lê Hoàng Nam', 'LE HOANG NAM', '1978-11-02',
                    '0905222111', '0905222111', '001078222111', '001078222111', 'SBV', 'SBV-0007'),

                   -- Ưu tiên 1
                   ('WARNING_CUSTOMER', 'Phạm Minh Tuấn', 'PHAM MINH TUAN', '1992-03-08',
                    '0978111333', '0978111333', '001092111333', '001092111333', 'EPAY_INTERNAL', 'WARN-0031'),
                   ('PEP', 'Vũ Đình Khánh', 'VU DINH KHANH', '1970-05-10',
                    '0913555666', '0913555666', '001070555666', '001070555666', 'PEP_VN', 'PEP-0114'),

                   -- Ưu tiên 3
                   ('FRAUD_SUSPECT', 'Đỗ Thị Lan', 'DO THI LAN', '1988-07-19',
                    '0966444555', '0966444555', '001188444555', '001188444555', 'C03', 'C03-2024-118'),
                   ('CRIMINAL_DEFENDANT', 'Bùi Văn Sơn', 'BUI VAN SON', '1983-09-25',
                    '0944777888', '0944777888', '001083777888', '001083777888', 'TAND', 'TAND-2023-441'),

                   -- Ưu tiên 7 — CÙNG một người với FRAUD_SUSPECT ở trên
                   ('STR_REPORTED', 'Đỗ Thị Lan', 'DO THI LAN', '1988-07-19',
                    '0966444555', '0966444555', '001188444555', '001188444555', 'AMLD', 'STR-2024-9902'),
                   ('EPAY_WATCH', 'Hoàng Thị Mai', 'HOANG THI MAI', '1995-12-01',
                    '0933888999', '0933888999', '001195888999', '001195888999', 'EPAY_INTERNAL', 'WATCH-0450')
    ) AS v(code, full_name, full_name_norm, dob, phone, phone_norm, id_number, id_number_norm, source, source_ref)
              ON c.code = v.code;


-- ------------------------- K2: quốc gia -------------------------
INSERT INTO watchlist_entry (category_id, match_type, country_code, source, source_ref)
SELECT c.id, 'K2', v.country_code, v.source, v.source_ref
FROM watchlist_category c
         JOIN (VALUES ('UN_SANCTION_COUNTRY', 'IR', 'UN_SC', 'UNSC-CTRY-IR'),
                      ('UN_SANCTION_COUNTRY', 'KP', 'UN_SC', 'UNSC-CTRY-KP'),
                      ('UN_SANCTION_COUNTRY', 'SY', 'UN_SC', 'UNSC-CTRY-SY'),

                      -- IR lặp lại có chủ ý: cùng một quốc gia ở hai danh sách khác mức ưu tiên
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


-- ------------------------- K3: nghề nghiệp -------------------------
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


-- ------------------------- K4: chức vụ -------------------------
INSERT INTO watchlist_entry (category_id, match_type, position_code, source, source_ref)
SELECT c.id, 'K4', v.position_code, v.source, v.source_ref
FROM watchlist_category c
         JOIN (VALUES ('HIGH_RISK_POSITION', 'SHELL_COMPANY_CEO', 'EPAY_INTERNAL', 'POS-H-01'),
                      ('HIGH_RISK_POSITION', 'OFFSHORE_BOARD_MEMBER', 'EPAY_INTERNAL', 'POS-H-02'),

                      ('MEDIUM_RISK_POSITION', 'DEPARTMENT_HEAD', 'EPAY_INTERNAL', 'POS-M-01')
    ) AS v(code, position_code, source, source_ref)
              ON c.code = v.code;


-- Đánh dấu mốc thay đổi để cache biết mà nạp.
UPDATE watchlist_category
SET entries_changed_at = now();
