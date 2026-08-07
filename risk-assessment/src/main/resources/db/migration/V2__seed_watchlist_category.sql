-- =====================================================================
-- Seed 16 danh sách: DS đen (priority 0) + 15 DS mẫu của bảng A.6
--
-- LƯU Ý Q2 (A.8): trong cùng priority, sub_order quyết định thứ tự duyệt.
-- Ở đây tạm chốt "nghiêm trọng hơn duyệt trước" (ví dụ priority 6:
-- chức vụ rủi ro CAO trước, TRUNG BÌNH sau). Phải xác nhận lại với BA.
-- =====================================================================

INSERT INTO watchlist_category (code, name, priority, sub_order, match_type, risk_level, risk_score, reason,
                                is_blacklist)
VALUES ('BLACKLIST', 'Danh sách đen', 0, 1, 'K1', 'CAO', 7, 'Trùng danh sách đen', TRUE),

       ('WARNING_CUSTOMER', 'DS khách hàng cảnh báo', 1, 1, 'K1', 'CAO', 5, 'Trùng khách hàng cảnh báo', FALSE),
       ('PEP', 'DS cá nhân có ảnh hưởng chính trị', 1, 2, 'K1', 'CAO', 5, 'Trùng cá nhân có ảnh hưởng chính trị',
        FALSE),

       ('UN_SANCTION_COUNTRY', 'DS quốc gia trừng phạt của LHQ', 2, 1, 'K2', 'CAO', 5,
        'Thuộc quốc gia trừng phạt của LHQ', FALSE),

       ('FRAUD_SUSPECT', 'DS KH nghi ngờ gian lận / vi phạm pháp luật hình sự', 3, 1, 'K1', 'CAO', 4,
        'KH nghi ngờ gian lận / phạm pháp', FALSE),
       ('CRIMINAL_DEFENDANT', 'DS KH là bị can / bị cáo / đã kết án', 3, 2, 'K1', 'CAO', 4,
        'KH là bị can / bị cáo / đã kết án', FALSE),

       ('FATF_HIGH_RISK_COUNTRY', 'DS quốc gia rủi ro cao do FATF công bố', 4, 1, 'K2', 'CAO', 4,
        'Quốc gia rủi ro FATF', FALSE),
       ('FINCEN_COUNTRY', 'DS quốc gia rủi ro rửa tiền hàng đầu của Fincen - Mỹ', 4, 2, 'K2', 'CAO', 4,
        'Quốc gia rủi ro rửa tiền - Fincen', FALSE),
       ('EU_TAX_HAVEN_COUNTRY', 'DS quốc gia thuộc Thiên đường thuế (EU blacklist)', 4, 3, 'K2', 'CAO', 4,
        'Quốc gia Thiên đường thuế (EU blacklist)', FALSE),

       ('HIGH_RISK_OCCUPATION', 'DS nghề nghiệp rủi ro cao', 5, 1, 'K3', 'CAO', 4, 'Nghề nghiệp rủi ro', FALSE),

       ('HIGH_RISK_POSITION', 'DS chức vụ rủi ro cao', 6, 1, 'K4', 'CAO', 4, 'Chức vụ rủi ro', FALSE),
       ('MEDIUM_RISK_POSITION', 'DS chức vụ rủi ro trung bình', 6, 2, 'K4', 'TRUNG_BINH', 3, 'Chức vụ rủi ro', FALSE),

       ('STR_REPORTED', 'DS khách hàng bị báo cáo giao dịch đáng ngờ', 7, 1, 'K1', 'TRUNG_BINH', 3,
        'Bị báo cáo GD đáng ngờ', FALSE),
       ('EPAY_WATCH', 'DS rà soát khác (do Epay theo dõi)', 7, 2, 'K1', 'TRUNG_BINH', 3,
        'Thuộc DS rà soát (Epay theo dõi)', FALSE),

       ('MEDIUM_RISK_OCCUPATION', 'DS nghề nghiệp rủi ro trung bình', 8, 1, 'K3', 'TRUNG_BINH', 3,
        'Nghề nghiệp rủi ro', FALSE),

       ('OTHER_WATCH_COUNTRY', 'DS quốc gia theo dõi khác', 9, 1, 'K2', 'TRUNG_BINH', 2,
        'Thuộc quốc gia theo dõi khác', FALSE);
