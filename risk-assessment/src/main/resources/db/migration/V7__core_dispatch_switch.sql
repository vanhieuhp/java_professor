-- =====================================================================
-- V7 — công tắc tạm dừng job gửi Core
--
-- Nảy ra từ một phép đo hỏng, nhưng là nhu cầu vận hành thật: khi Core có cửa sổ bảo trì,
-- để job tiếp tục gửi nghĩa là mọi kết quả đang chờ sẽ đếm đủ số lần thử rồi chuyển sang
-- FAILED, và sau đó phải gọi tay /core-dispatch/requeue-failed để cứu. Ngừng gửi thì chúng
-- nằm yên ở PENDING và tự đi tiếp khi bật lại.
--
-- Phải là một DÒNG trong bảng, không phải giá trị mặc định trong code: PcrtConfigService.set
-- chạy câu UPDATE, không có dòng thì cập nhật 0 dòng và công tắc im lặng không có tác dụng.
-- =====================================================================
INSERT INTO pcrt_config (config_key, config_value, description)
VALUES ('core.dispatch.enabled', 'true',
        'Bật/tắt job gửi kết quả sang Core. Tắt khi Core bảo trì — kết quả nằm ở PENDING chứ không bị đẩy sang FAILED.')
ON CONFLICT (config_key) DO NOTHING;
