# Core publish sự kiện — PCRT lắng nghe và rà soát

Trạng thái: **xong, đã nghiệm thu bằng đo thật**. Ngày 06/08/2026.

---

## 1. Đổi cái gì

**Trước:** BE ví gọi thẳng `POST /customers/evaluate`. Đồng bộ. Core phải *biết* PCRT tồn tại,
và PCRT chết thì Core phải tự quyết: chặn khách hàng lại, hay cho qua rồi quên mất là chưa ai
rà soát người này. Cả hai lựa chọn đều sai.

**Sau:** Core publish sự kiện rồi đi tiếp. PCRT chết thì sự kiện nằm lại trong topic; PCRT sống
dậy đọc tiếp từ offset cũ.

```
core.wallet_customer đổi
        │
        ▼
MockCoreCustomerChanger ──publish──► core.customer.changed  (khóa = CIF)
                                              │
                                              ▼
                              CustomerChangedEventConsumer
                                  · giải mã JSON
                                  · ack SAU khi commit
                                              │
                                              ▼
                              CustomerChangeProcessor  ◄──── CoreEventController (REST dự phòng)
                                  · xác thực
                                  · giành vé chống trùng
                                  · định tuyến
                                              │
              ┌───────────────┬───────────────┼───────────────┐
              ▼               ▼               ▼               ▼
          RETIRED        MIRRORED_ONLY      STALE          SCREENED
        (bia mộ)      (chỉ bản chiếu)   (bỏ, ghi chú)   (TH2 đầy đủ)
```

Đường TH2 cũ **giữ nguyên**. `CustomerEvaluationService`, `ScanQueueMapper`,
`ScanRecordProcessor`, `RiskEvaluator`, hàng đợi gửi Core — không sửa một dòng. Thứ duy nhất
thêm vào là một cái vòi mới đổ nước vào cùng đường ống.

---

## 2. Sáu kết cục, và vì sao không gộp thành "thành công / thất bại"

| Mã | Nghĩa |
|---|---|
| `SCREENED` | Chạy đủ TH2: bản chiếu → hàng đợi → so khớp DS đen → ghi kết quả |
| `MIRRORED_ONLY` | Chỉ cập nhật bản chiếu. KH đã khóa/đóng ví, hoặc là tổ chức |
| `RETIRED` | `DELETED` — đặt bia mộ, gỡ khỏi tập quét |
| `STALE` | Sự kiện chở dữ liệu **cũ hơn** bản chiếu. Bỏ, có ghi chú |
| `DUPLICATE` | Đã xử lý ở lần nhận trước |
| `REJECTED` | Không qua xác thực. Đã ghi inbox kèm lý do |

Gộp lại thành hai mã sẽ giấu mất trường hợp nguy hiểm nhất: `MIRRORED_ONLY` tăng vọt nghĩa là
hàng loạt khách hàng đang bị coi là ngoài tập quét. Có thể đúng (một đợt đóng ví), có thể là
Core gửi sai `status`. Nhìn vào một con số "thành công" thì hai chuyện đó giống hệt nhau.

---

## 3. Nghiệm thu — 11 kịch bản, chạy thật

| # | Kịch bản | Kết quả |
|---|---|---|
| 1 | Core đổi tên+dob trùng DS đen | `SCREENED`, điểm 7, `entry_id=3`, `FULL_NAME,DOB`, yêu cầu khóa CIF |
| 2 | Cùng `eventId`, vào bằng REST | `DUPLICATE` — không sinh gì thêm |
| 3 | `eventId` mới, `occurredAt` cũ | `STALE` — bản chiếu **không** bị kéo lùi |
| 4 | Thiếu `fullName` | `REJECTED`, inbox giữ lý do |
| 5 | `status` lạ + `dob` tương lai | `REJECTED`, **hai** lý do trong một dòng |
| 6 | Thiếu `eventId` | 400, hỏng cấu trúc |
| 7 | `changeType: "MERGED"` | 400, hỏng cấu trúc — **không** ném stack trace Jackson |
| 8 | Core khóa ví | `MIRRORED_ONLY`, `scan_target → false` |
| 9 | Core xóa KH | `RETIRED`, bia mộ, `scan_target → false` |
| 10 | JSON hỏng vào thẳng topic | `kafkaPoisoned=1`, ack, partition đi tiếp |
| 11 | Sự kiện tốt **sau** gói tin hỏng, cùng khóa | `SCREENED` — partition **không** đứng im |

### Phép thử quyết định: phát lại toàn bộ topic

```bash
kafka-consumer-groups.sh --group pcrt-cif --topic core.customer.changed \
    --reset-offsets --to-earliest --execute
```

| | Trước | Sau khi phát lại |
|---|---:|---:|
| `pcrt_core_event_inbox` | 7 | **7** |
| `customer_risk_result` | 4 | **4** |
| `customer_scan_queue` | 6 | **6** |
| bia mộ | 1 | **1** |

Nhận lại 5 sự kiện → 4 `DUPLICATE` + 1 hỏng. **Không một dòng nào được sinh thêm.**

Đó là toàn bộ ý nghĩa của cụm "chỉ một lần về mặt hiệu ứng": Kafka vẫn giao lại, hệ thống
không cố ngăn điều đó, nó chỉ làm cho việc giao lại trở nên vô hại.

---

## 4. Bốn quyết định đáng nhớ

### Khóa phân vùng là CIF, không phải eventId

Kafka chỉ bảo đảm thứ tự **trong một partition**. Lấy `eventId` làm khóa thì hai thay đổi liên
tiếp của cùng một khách hàng rơi vào hai partition, hai luồng xử lý song song, thứ tự thành
ngẫu nhiên — bản chiếu có thể nhận "đổi tên thành X" sau "đổi tên thành Y" dù Core làm ngược lại.

Chốt thứ tự `core_updated_at` vẫn giữ nguyên, nhưng nó là **lưới an toàn**, không phải cơ chế
chính. Lưới chỉ nên đỡ trường hợp hiếm.

### Sự kiện mang toàn bộ trạng thái, không phải phần thay đổi

Gói tin to hơn nhiều so với kiểu delta. Nhưng luật K1 cần đủ 4 trường cùng lúc: đổi mỗi số điện
thoại vẫn có thể làm khách hàng trùng "tên + SĐT" với một bản ghi DS đen. Nhận delta thì PCRT
phải đi đọc lại Core để biết ba trường còn lại — tức là quay về đúng chỗ mà việc publish sự
kiện đang cố thoát ra.

### `STALE` phải dừng lại, không được chấm tiếp

Nếu vẫn chấm bằng dữ liệu cũ, kết quả sinh ra mang cờ `is_latest` và **đè lên** kết quả mới hơn
(`uq_result_latest_per_cif`). Một sự kiện gửi lại sau lỗi mạng sẽ lặng lẽ kéo lùi hồ sơ rủi ro
của khách hàng.

Đã kiểm chứng ở kịch bản 3: bản chiếu giữ nguyên `NGUYEN VAN AN / 1990-01-15`.

### Toàn bộ xử lý là MỘT transaction

Tấm vé chống trùng (dòng inbox) và hiệu ứng của nó phải cùng bền hóa hoặc cùng biến mất. Commit
riêng tấm vé rồi hỏng ở bước sau nghĩa là mọi lần gửi lại đều bị chặn ở cổng, và khách hàng đó
**không bao giờ được rà soát**, im lặng.

Ở đây làm được vì hiệu ứng chỉ là ghi Postgres — lời gọi sang Core nằm ở job khác đọc bảng
`customer_risk_result`. Ở pcrt-lab hiệu ứng nằm trong Redis nên transaction không bao trọn được
và hai mốc phải tách. Khác biệt do **bản chất hiệu ứng**, không phải sở thích.

---

## 5. Hai lỗi phép đo tự tìm ra

Cả hai đều chỉ lộ khi nhìn vào dữ liệu thật, không lộ khi đọc code.

**`STALE` bị đóng sổ sạch sẽ.** Ban đầu `processed_at` được đặt, `process_error` để NULL — sự
kiện biến mất khỏi mọi truy vấn. Nguy hiểm ở quy mô: nếu đồng hồ Core chạy lùi thì **toàn bộ**
sự kiện thành `STALE` và không khách hàng nào được rà soát nữa, trong khi mọi con số đều xanh,
inbox không có dòng nào tồn, không lỗi nào được ném. Sửa: vẫn đóng sổ (thử lại vô ích) nhưng
để lại ghi chú đếm được.

**Bộ đếm nằm sai chỗ.** Ban đầu `byOutcome` đếm trong consumer Kafka, nên các sự kiện vào bằng
REST không được đếm. Đúng lúc Kafka chết và mọi thứ chạy qua đường dự phòng thì bảng số liệu
hiển thị 0 — mù đúng lúc cần nhìn nhất. Chuyển bộ đếm vào `CustomerChangeProcessor`, nơi cả hai
đường đều đi qua.

Đây cũng là lý do nghiệp vụ **không** nằm trong consumer: bài học đã trả giá ở pcrt-lab Phase 2,
nơi đường REST dự phòng nhận sự kiện, trả 200, và không chấm điểm gì cả.

---

## 6. Món nợ đã trả và món nợ mới

### Trả xong: bản chiếu định danh giờ có đường RA

Từ V6 tới trước hôm nay, `pcrt_customer_identity` chỉ có đường vào. Core xóa một khách hàng thì
dòng cũ nằm lại vĩnh viễn với `scan_target = true`; hệ thống ghi log `"bản chiếu lệch"` nhưng
không bao giờ tự sửa.

`DELETED` đóng nốt phần đó. **Xóa mềm**, không `DELETE` hẳn:

- một sự kiện `UPDATED` cũ tới muộn **sau** lệnh xóa sẽ không tìm thấy dòng nào để đụng vào
  `ON CONFLICT`, nên nó chèn dòng mới — khách hàng đã xóa **sống lại** với `scan_target = true`;
- `"vì sao CIF này biến mất khỏi tập quét"` phải trả lời được.

Không cần index mới: cả 5 index so khớp đều có vị từ `WHERE scan_target AND ...`, mà bia mộ luôn
hạ `scan_target`. Dòng bia mộ tự rơi khỏi index.

### Nợ mới

| Món | Vì sao còn |
|---|---|
| **Dual-write bên Core** — DB commit rồi publish hỏng thì PCRT không bao giờ biết | Lời giải là outbox **bên Core**, thuộc codebase khác. Lưới đỡ hiện có: job đồng bộ delta + quét xuôi hằng đêm. Chậm hơn nhiều, nhưng không im lặng |
| **Sự kiện hỏng cấu trúc chỉ có trong log** — thiếu `eventId`/`cif`/`changeType` thì không ghi nổi inbox (chính các cột đó là NOT NULL) | Cần một bảng DLQ. Nhóm lỗi nghiệp vụ đã ghi được inbox rồi |
| **Chưa có job dọn bia mộ** | Bia mộ cũ hơn N ngày nên được xóa hẳn |
| **`normalizer_version` vẫn là trang trí** | Hard-code `1` ở mọi đường ghi. Không job backfill, không query nào lọc theo nó |
| **Job đồng bộ delta vẫn chưa lên lịch** | `PcrtSchedulingConfig` chỉ đăng ký quét định kỳ và gửi Core |

---

## 7. Cách chạy lại

```bash
docker compose -f docker/docker-compose.yml up -d
```

```bash
curl -s -X POST "http://localhost:8080/mock-core/customers/CIF00049998/change?changeType=UPDATED" -H "Content-Type: application/json" -d '{"fullName":"Nguyễn Văn An","dob":"1990-01-15"}'
```

```bash
curl -s http://localhost:8080/api/v1/pcrt/core-events/stats
```

```bash
curl -s "http://localhost:8080/api/v1/pcrt/core-events/rejected?limit=20"
```

```bash
docker exec pcrt-cif-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9094 --group pcrt-cif --topic core.customer.changed --reset-offsets --to-earliest --execute
```

> Lệnh reset chỉ chạy được khi app đã tắt và consumer group rời hẳn — chờ khoảng 15 giây sau
> khi tắt, nếu không Kafka báo `group is not inactive`.
