-- =====================================================================
-- V9 — tách mốc đồng bộ delta ra khỏi dữ liệu
--
-- Trước: mốc lấy bằng SELECT max(core_updated_at) FROM pcrt_customer_identity.
-- Cách đó chỉ đúng khi mọi người ghi vào bảng đều đóng dấu thời gian từ cùng một đồng hồ.
-- Từ khi TH2 được phép ghi, giá trị đó do BE ví CN gửi sang — tức do một hệ thống KHÁC quyết
-- định. Một sự kiện mang mốc tương lai (lệch đồng hồ, dữ liệu test, lỗi múi giờ) sẽ đẩy mốc
-- vượt qua hiện tại, và từ đó câu WHERE update_time > :since không còn khớp dòng nào nữa.
-- Job vẫn chạy, vẫn báo thành công, vẫn ghi "đồng bộ 0 dòng" — và bản chiếu đứng yên mãi mãi.
--
-- Mốc đồng bộ là trạng thái của JOB, không phải thuộc tính của dữ liệu. Nên nó phải nằm ở
-- chỗ chỉ job ghi được, và giá trị lấy từ đồng hồ của database chứ không của ai khác.
-- =====================================================================
INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('identity.sync.watermark', '1970-01-01T00:00:00Z',
        'Mốc đồng bộ delta bản chiếu định danh. CHỈ job đồng bộ được ghi, và luôn ghi bằng '
            || 'giờ của database lấy lúc BẮT ĐẦU lượt chạy — lấy lúc kết thúc sẽ bỏ sót các '
            || 'thay đổi xảy ra trong khi lượt đó đang chạy.')
ON CONFLICT (config_key) DO NOTHING;
