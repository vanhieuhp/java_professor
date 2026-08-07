-- =====================================================================
-- V8 — mở đường cho TH2 ghi thẳng vào bản chiếu định danh
--
-- Tới giờ bản chiếu chỉ có một nguồn ghi: job đồng bộ đọc core.wallet_customer. Nguồn đó
-- luôn biết id bên Core. TH2 thì không — nó nhận dữ liệu do BE ví CN đẩy sang, và trong
-- payload đó không có khóa chính của bảng bên Core.
--
-- Ba cách xử lý, chọn cách thứ ba:
--   1. Bắt BE ví CN gửi kèm core_id — bắt hệ thống khác lộ khóa nội bộ của nó ra ngoài
--      chỉ vì bảng của mình cần, và từ đó hai bên dính chặt vào nhau.
--   2. Đọc ngược core.wallet_customer theo CIF để lấy id — thêm một round-trip vào đúng
--      đường realtime mà TH2 sinh ra để tránh.
--   3. Cho phép NULL. Lần đồng bộ kế tiếp sẽ điền vào.
--
-- Chọn được cách 3 vì core_id không phải khóa nghiệp vụ: CIF mới là khóa chính, và mọi
-- phép đối chiếu với Core đều đi theo CIF. core_id chỉ để soi ngược khi cần điều tra.
-- =====================================================================
ALTER TABLE pcrt_customer_identity ALTER COLUMN core_id DROP NOT NULL;

COMMENT ON COLUMN pcrt_customer_identity.core_id IS
    'id bên Core. NULL với dòng do TH2 tạo trước khi lần đồng bộ đầu tiên chạm tới — CIF mới là khóa.';

COMMENT ON COLUMN pcrt_customer_identity.core_updated_at IS
    'Mốc thứ tự, KHÔNG phải mốc kiểm toán. Ghi đè chỉ xảy ra khi mốc mới >= mốc đang có, '
        'nên một sự kiện TH2 tới muộn không thể đè lên dữ liệu mới hơn.';
