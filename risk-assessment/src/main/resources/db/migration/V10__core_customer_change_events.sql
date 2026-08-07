-- =====================================================================
-- V10 — Core publish sự kiện đổi thông tin KH, PCRT lắng nghe
--
-- TRƯỚC: BE ví gọi thẳng POST /customers/evaluate. Đồng bộ, và Core phải BIẾT PCRT tồn tại.
-- PCRT chết thì lời gọi hỏng, và Core phải tự quyết định: chặn khách hàng lại, hay cho qua
-- rồi quên mất là chưa ai rà soát người này. Cả hai lựa chọn đều sai.
--
-- SAU: Core publish sự kiện rồi đi tiếp. PCRT chết thì sự kiện nằm lại trong topic; PCRT
-- sống dậy đọc tiếp từ offset cũ. Không mất, và Core không cần biết ai đang nghe.
--
-- Cái giá phải trả, và không giấu được: Kafka chỉ hứa AT-LEAST-ONCE. Cùng một sự kiện SẼ
-- tới hai lần — khi consumer chết sau lúc xử lý nhưng trước lúc commit offset, khi consumer
-- group rebalance, khi producer gửi lại vì không nhận được ack. Đó là vận hành bình thường,
-- không phải sự cố hiếm.
--
-- Cách duy nhất đúng là làm cho HIỆU ỨNG không lặp lại, chứ không phải cố làm cho việc giao
-- nhận không lặp lại. Bảng inbox bên dưới giữ lời hứa đó bằng một UNIQUE constraint của
-- database — không phải bằng một câu SELECT kiểm tra trước.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Inbox — cổng chống trùng
--
-- Cùng thiết kế với pcrt_event_inbox của pcrt-lab, và đó là chủ ý: hai hệ thống khác nhau
-- nhưng bài toán "sự kiện tới hai lần" thì giống hệt, nên lời giải phải giống hệt. Người
-- đọc hiểu bảng này ở một bên là hiểu luôn bên kia.
-- ---------------------------------------------------------------------
CREATE TABLE pcrt_core_event_inbox
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Cặp (event_type, event_id) chứ không phải mình event_id: các loại sự kiện do các hệ
    -- thống khác nhau sinh ra, không có gì bảo đảm id của chúng không đụng nhau.
    event_type    VARCHAR(24)  NOT NULL,

    -- Khóa chống trùng, do NGƯỜI GỬI sinh và nằm sẵn trong sự kiện.
    -- Tuyệt đối không được là offset Kafka hay một UUID do PCRT tự sinh lúc nhận: cùng một
    -- thay đổi vào bằng cả Kafka lẫn REST dự phòng — chuyện chắc chắn xảy ra khi Core bật
    -- đường dự phòng lúc Kafka đang lag chứ chưa chết hẳn — sẽ được coi là hai sự kiện khác
    -- nhau và bị xử lý hai lần.
    event_id      VARCHAR(64)  NOT NULL,

    cif           VARCHAR(50)  NOT NULL,
    change_type   VARCHAR(20)  NOT NULL,

    -- KAFKA là đường chính, REST là đường dự phòng khi Kafka chết.
    source        VARCHAR(16)  NOT NULL,

    -- Payload THÔ, y nguyên byte nhận được. Không phải để dùng hằng ngày mà để giải trình:
    -- khi kết quả rà soát bị chất vấn, phải chứng minh được đầu vào lúc đó là gì, kể cả khi
    -- code phân tích payload sau này đã đổi.
    payload       JSONB        NOT NULL,

    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Ở đây processed_at được đặt trong CÙNG transaction với hiệu ứng, khác với pcrt-lab.
    -- Lý do: hiệu ứng của PCRT là ghi Postgres (bản chiếu + hàng đợi + kết quả), nên
    -- transaction bao trọn được. Ở pcrt-lab hiệu ứng nằm ở Redis nên phải tách.
    -- Cột vẫn để NULLable vì đường REST dự phòng và job chạy lại cần phân biệt.
    processed_at  TIMESTAMPTZ,
    process_error VARCHAR(1000),

    CONSTRAINT uq_core_inbox_event UNIQUE (event_type, event_id),
    CONSTRAINT ck_core_inbox_type CHECK (event_type IN ('CUSTOMER_CHANGED')),
    CONSTRAINT ck_core_inbox_source CHECK (source IN ('KAFKA', 'REST')),
    CONSTRAINT ck_core_inbox_change CHECK (change_type IN ('CREATED', 'UPDATED', 'STATUS_CHANGED', 'DELETED'))
);

-- Partial index: dòng nào xử lý xong là tự rời khỏi index, nên index luôn chỉ lớn bằng
-- đúng lượng việc còn tồn. Bảng inbox sẽ có hàng chục triệu dòng, hàng đợi thì không.
CREATE INDEX idx_core_inbox_unprocessed ON pcrt_core_event_inbox (id) WHERE processed_at IS NULL;

-- Truy vết theo khách hàng khi điều tra: "CIF này đã có những thay đổi nào, theo thứ tự nào".
CREATE INDEX idx_core_inbox_cif ON pcrt_core_event_inbox (cif, received_at DESC);


-- ---------------------------------------------------------------------
-- 2. Đường RA của bản chiếu định danh
--
-- Từ V6 tới giờ pcrt_customer_identity chỉ có đường VÀO. Core xóa một khách hàng thì dòng
-- cũ nằm lại vĩnh viễn với scan_target = true, và quét ngược vẫn trả nó về làm ứng viên.
-- Hệ thống có ghi log cảnh báo "bản chiếu lệch" nhưng KHÔNG BAO GIỜ tự sửa.
--
-- Sự kiện DELETED chính là đường ra còn thiếu.
--
-- Vì sao XÓA MỀM chứ không DELETE hẳn:
--   1. Chốt thứ tự. Một sự kiện UPDATE cũ tới muộn SAU lệnh xóa sẽ không thấy dòng nào để
--      đụng vào ON CONFLICT, nên nó INSERT dòng mới — khách hàng đã xóa sống lại. Giữ dòng
--      lại thì điều kiện core_updated_at <= EXCLUDED.core_updated_at vẫn chặn được.
--   2. Truy vết. "Vì sao CIF này biến mất khỏi tập quét" phải trả lời được.
-- Dọn hẳn là việc của job xóa bia mộ cũ hơn N ngày, không phải của đường xử lý sự kiện.
-- ---------------------------------------------------------------------
ALTER TABLE pcrt_customer_identity
    ADD COLUMN deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN pcrt_customer_identity.deleted_at IS
    'Bia mộ. NOT NULL = Core báo KH này không còn. Luôn đi kèm scan_target = false, nên 5 '
        'partial index (đều có vị từ scan_target) tự động loại dòng này ra.';

-- Không cần index mới: cả 5 index so khớp đều có vị từ `WHERE scan_target AND ...`, mà
-- xóa mềm luôn hạ scan_target xuống false. Dòng bia mộ tự rơi khỏi index.


INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('core.event.rest-fallback.enabled', 'true',
        'Bật đường REST dự phòng /api/v1/pcrt/core-events khi Kafka chết. Cùng cổng chống '
            || 'trùng với đường Kafka nên bật cả hai cùng lúc là an toàn.');
