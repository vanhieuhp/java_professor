# Lộ trình học & xây dựng Hệ thống Đánh giá Rủi ro Rửa tiền Khách hàng (PCRT)
### Java / Spring Boot — bám sát 100% spec nghiệp vụ đã cung cấp

---

## PHẦN A — CHUẨN HÓA SPEC (nguồn chân lý duy nhất)

Trước khi học, phần này chốt lại **toàn bộ** yêu cầu nghiệp vụ đã cung cấp, dạng có thể truy vết. Mọi phase phía sau đều tham chiếu về mã số ở đây.

### A.1 — Điều kiện kích hoạt chấm điểm (3 trigger)

| Mã | Trigger | Phạm vi khách hàng quét | Danh sách so khớp | Flow |
|---|---|---|---|---|
| **T1** | Dữ liệu DS đen (cấu hình trong hệ thống) được điều chỉnh | Realtime, **toàn bộ** KH **cá nhân** ví trạng thái Active/Approved | **Chỉ DS đen** | TH1 |
| **T2** | KH mới mở hoặc cập nhật thông tin tài khoản ví | Realtime, **1 khách hàng** vừa phát sinh | **Chỉ DS đen** | TH2 |
| **T3a** | Đánh giá định kỳ — DS mẫu (khác DS đen) **được điều chỉnh** | Realtime, **toàn bộ** KH cá nhân ví Active/Approved | **Toàn bộ tiêu chí** (9 nhóm DS mẫu) | TH3 |
| **T3b** | Đánh giá định kỳ — DS mẫu **không** điều chỉnh; KH mới mở/cập nhật thành công **mà không trùng DS đen** | KH tạo mới/cập nhật thành công ngày T-1, Active/Approved, **điểm ≠ 7** | **Toàn bộ tiêu chí trừ DS đen** | TH3 |

### A.2 — Nguyên tắc chấm điểm chung

- Hệ thống so khớp thông tin KH cá nhân với các danh sách mẫu.
- Nếu thông tin KH trùng **bất kỳ** thông tin mẫu nào → KH được xác định **có rủi ro rửa tiền**.
- Hệ thống gán **điểm** và **mức độ rủi ro** tương ứng với thông tin trùng.

### A.3 — Flow TH1: DS đen được điều chỉnh

| Bước | Nội dung |
|---|---|
| B1 | Khi DS đen được điều chỉnh, Service PCRT đọc DB Core ví lấy **tất cả** KH trạng thái Active/Approved, đánh giá **realtime**. |
| B2 | PCRT lưu danh sách KH cần đánh giá vào **DB PCRT**, trạng thái **Chờ xử lý (CXL)**. |
| B3 | **Xử lý tuần tự** tất cả bản ghi đã lưu ở B2. So khớp lần lượt với **DS đen mới**. Trường so khớp: **Số GTTT, họ tên, ngày sinh, số điện thoại**.<br>• **Trùng** = trùng Số GTTT **HOẶC** trùng **2 thông tin bất kỳ** với **cùng 1 bản ghi** trong DS đen → cập nhật trạng thái bản ghi = **'Đã xử lý'**, sinh bản ghi KH rủi ro: **mức rủi ro cao, điểm = 7**, trả kết quả cho Core ví → chuyển **đồng thời** B4 và B5.<br>• **Kết quả khác** → cập nhật trạng thái = **'Đã xử lý'** → chuyển B5. |
| B4 | Core tiếp nhận kết quả, cập nhật kết quả đánh giá rủi ro vào CSDL. **Đồng thời thực hiện quy trình khóa CIF** khách hàng (refer tài liệu Đóng/Khóa ví) → kết thúc. |
| B5 | PCRT kiểm tra còn KH chưa được đánh giá không: còn → quay lại B3; không còn → kết thúc. |

### A.4 — Flow TH2: KH đăng ký/cập nhật tài khoản ví thành công

| Bước | Nội dung |
|---|---|
| B1 | Sau khi KH eKYC tài khoản ví thành công **hoặc** cập nhật thông tin thành công, **BE ví CN** gửi thông tin KH sang PCRT.<br>**Bắt buộc**: số CIF, số GTTT, họ tên, ngày sinh, số điện thoại.<br>**Không bắt buộc**: quốc gia, **Số GTTT cũ**. |
| B2 | PCRT sinh bản ghi trong bảng DS KH cần đánh giá (**trạng thái Chờ xử lý**) và so khớp với **DS đen**. Trường so khớp: Số GTTT, họ tên, ngày sinh, số điện thoại.<br>• **Trùng Số GTTT HOẶC ≥2 thông tin bất kỳ ở cùng 1 bản ghi DS đen** → cập nhật trạng thái = 'Đã xử lý', sinh bản ghi KH rủi ro **mức cao, điểm = 7** → chuyển B3.<br>• **Kết quả khác** → cập nhật = 'Đã xử lý' → **kết thúc**. |
| B3 | PCRT gửi kết quả về Core ví. Thông tin gồm: **số CIF, mức rủi ro, điểm rủi ro, Lý do**. |
| B4 | Core ví tiếp nhận, cập nhật **mức rủi ro + điểm rủi ro** vào CSDL. **Đồng thời thực hiện quy trình khóa CIF** (refer tài liệu Đóng/Khóa ví). Kết thúc. |

### A.5 — Flow TH3: Đánh giá định kỳ

| Bước | Nội dung |
|---|---|
| B1 | Vào **x giờ hàng ngày** (**x cấu hình trong CSDL PCRT**), PCRT kiểm tra DS mẫu (khác DS đen) có được điều chỉnh **trong ngày liền trước** không.<br>• **Có điều chỉnh** → đọc DB Core lấy **toàn bộ** KH **CN** ví trạng thái Active/Approved → B2.<br>• **Không điều chỉnh** → đọc DB Core lấy KH thỏa mãn **đồng thời**: (a) trạng thái Active/Approved; (b) tạo mới thành công trong ngày liền trước **hoặc** điều chỉnh thông tin thành công trong ngày liền trước (dựa vào **update_time**); [theo sơ đồ: **điểm ≠ 7**] → B2. |
| B2 | PCRT lưu danh sách KH cần đánh giá vào DB PCRT, trạng thái **Chờ xử lý**. |
| B3 | Xử lý **lần lượt** các bản ghi đã lưu ở B2. Với mỗi bản ghi, so khớp với các DS mẫu (khác DS đen) theo **thứ tự mức ưu tiên tăng dần**.<br>• **Trùng bất kỳ** → **dừng so khớp ngay**, cập nhật trạng thái = 'Đã xử lý', sinh bản ghi kết quả rủi ro trong CSDL PCRT với **điểm và mức độ tương ứng bản ghi trùng ĐẦU TIÊN tìm thấy**, trả kết quả cho Core → chuyển **đồng thời** B4 và B5.<br>• **Không trùng bản ghi nào** → chuyển B5. |
| B4 | Core tiếp nhận, cập nhật kết quả đánh giá rủi ro vào CSDL → kết thúc. **(Không có bước khóa CIF — khác TH1/TH2)** |
| B5 | Còn KH chưa đánh giá → quay lại B3; không còn → kết thúc. |

### A.6 — Bảng 9 nhóm DS mẫu (cấu hình trong CSDL, kèm mức ưu tiên/mức rủi ro/điểm)

| Ưu tiên | Tên danh sách | Thông tin so khớp | Mức / Điểm | Lý do |
|---|---|---|---|---|
| **1** | DS khách hàng cảnh báo | Số GTTT, họ tên, ngày sinh, số điện thoại *(trùng nếu trùng Số GTTT hoặc trùng 2 thông tin bất kỳ với cùng 1 cá nhân trong DS mẫu)* | Cao / **5** | Trùng khách hàng cảnh báo |
| **1** | DS cá nhân có ảnh hưởng chính trị | (như trên) | Cao / **5** | Trùng cá nhân có ảnh hưởng chính trị |
| **2** | DS quốc gia trừng phạt của LHQ | **Quốc gia trong địa chỉ** của KH | Cao / **5** | Thuộc quốc gia trừng phạt của LHQ |
| **3** | DS KH nghi ngờ gian lận / vi phạm pháp luật hình sự | Số GTTT, họ tên, ngày sinh, số điện thoại *(quy tắc trùng như ưu tiên 1)* | Cao / **4** | KH nghi ngờ gian lận / phạm pháp |
| **3** | DS KH là bị can / bị cáo / đã kết án | (như trên) | Cao / **4** | KH là bị can / bị cáo / đã kết án |
| **4** | DS quốc gia rủi ro cao do **FATF** công bố | Quốc gia trong địa chỉ của KH | Cao / **4** | Quốc gia rủi ro FATF |
| **4** | DS quốc gia rủi ro rửa tiền hàng đầu của **Fincen – Mỹ** | Quốc gia trong địa chỉ của KH | Cao / **4** | Quốc gia rủi ro rửa tiền – Fincen |
| **4** | DS quốc gia thuộc **Thiên đường thuế (EU blacklist)** | Quốc gia trong địa chỉ của KH | Cao / **4** | Quốc gia Thiên đường thuế (EU blacklist) |
| **5** | DS nghề nghiệp rủi ro cao | **Nghề nghiệp** của KH | Cao / **4** | Nghề nghiệp rủi ro |
| **6** | DS chức vụ rủi ro cao | **Chức vụ** của KH | Cao / **4** | Chức vụ rủi ro |
| **6** | DS chức vụ rủi ro trung bình | Chức vụ của KH | **Trung bình / 3** | Chức vụ rủi ro |
| **7** | DS khách hàng bị báo cáo giao dịch đáng ngờ | Số GTTT, họ tên, ngày sinh, số điện thoại *(quy tắc trùng như ưu tiên 1)* | Trung bình / **3** | Bị báo cáo GD đáng ngờ |
| **7** | DS rà soát khác (do Epay theo dõi) | (như trên) | Trung bình / **3** | Thuộc DS rà soát (Epay theo dõi) |
| **8** | DS nghề nghiệp rủi ro trung bình | Nghề nghiệp của KH | Trung bình / **3** | Nghề nghiệp rủi ro |
| **9** | DS quốc gia theo dõi khác | Quốc gia trong địa chỉ của KH | Trung bình / **2** | Thuộc quốc gia theo dõi khác |

**Ngoài bảng**: **DS đen** → mức **cao**, điểm **7** (cao nhất, chỉ dùng ở TH1/TH2, kèm khóa CIF).

### A.7 — 4 kiểu tiêu chí so khớp (rút ra từ A.6)

| Kiểu | Áp dụng cho ưu tiên | Trường KH cần có |
|---|---|---|
| **K1 — Định danh cá nhân** (GTTT / họ tên / ngày sinh / SĐT, rule "GTTT hoặc ≥2 trường cùng 1 bản ghi") | 1, 3, 7 + **DS đen** | 4 trường định danh |
| **K2 — Quốc gia** (trong địa chỉ KH) | 2, 4, 9 | quốc gia |
| **K3 — Nghề nghiệp** | 5, 8 | nghề nghiệp |
| **K4 — Chức vụ** | 6 | chức vụ |

### A.8 — Danh sách điểm chưa rõ trong spec (phải chốt trước khi code)

| Mã | Vấn đề | Ảnh hưởng phase |
|---|---|---|
| **Q1** | TH3 B4 **không có** bước khóa CIF (khác TH1/TH2) — chủ ý hay thiếu? | P5 |
| **Q2** | Ưu tiên 6 có 2 DS (cao/4 và TB/3) cùng mức ưu tiên — nếu trùng cả hai thì lấy DS nào? Tương tự ưu tiên 1, 3, 4, 7 có nhiều DS con — thứ tự duyệt trong cùng priority? | P2 |
| **Q3** | **Số GTTT cũ** (optional ở TH2) có được dùng để so khớp DS đen không? | P2, P4 |
| **Q4** | Sơ đồ TH3 nhánh "không điều chỉnh DS mẫu" có điều kiện **điểm ≠ 7**, phần text B1 không nhắc — lấy điểm này từ DB Core hay DB PCRT? | P1, P3 |
| **Q5** | KH đã có bản ghi rủi ro, khi quét lại thì **ghi đè** hay **append lịch sử**? | P1 |
| **Q6** | Tiêu chí xác định "DS đen được điều chỉnh" / "DS mẫu được điều chỉnh" — theo `updated_at` bản ghi, hay có bảng version/changelog riêng? | P1, P3 |
| **Q7** | So khớp K2 (quốc gia): so theo mã quốc gia hay tên? Địa chỉ KH lưu dạng gì trong Core? | P2 |
| **Q8** | So khớp K3/K4 (nghề nghiệp/chức vụ): mã hóa danh mục hay free-text? Nếu free-text thì so khớp chính xác hay chứa? | P2 |
| **Q9** | TH1/TH3 quét toàn bộ: KH nào đang ở trạng thái Chờ xử lý của lần quét trước chưa xong thì xử lý thế nào khi có trigger mới? | P3 |
| **Q10** | Tài liệu **Đóng/Khóa ví** được refer nhưng chưa có nội dung. | P5 |

---

## PHẦN B — LỘ TRÌNH HỌC 8 PHASE

| Phase | Nội dung | Phủ spec |
|---|---|---|
| 0 | Nền tảng nghiệp vụ AML/KYC | A.1, A.2, A.6 |
| 1 | Data modeling & schema | A.1–A.7 (toàn bộ), Q4–Q6 |
| 2 | **Matching engine (4 kiểu tiêu chí + priority)** | A.3-B3, A.4-B2, A.5-B3, A.6, A.7, Q2/Q3/Q7/Q8 |
| 3 | **Batch flow (TH1 + TH3)** | A.3, A.5, Q9 |
| 4 | Realtime flow (TH2) | A.4 |
| 5 | Tích hợp PCRT ↔ Core ví + khóa CIF | A.3-B4, A.4-B3/B4, A.5-B4, Q1, Q10 |
| 6 | Testing toàn bộ 3 flow | Tất cả |
| 7 | Scale & production (học sau) | — |

---

### Phase 0 — Nền tảng nghiệp vụ AML/KYC

**Mục tiêu:** hiểu *tại sao* spec được thiết kế như vậy, để khi code gặp tình huống spec chưa nói vẫn quyết định đúng.

**Nội dung học:**
1. Watchlist screening — sanctions list, PEP (chính là "DS cá nhân có ảnh hưởng chính trị" ưu tiên 1), adverse media. Nguồn thật của các DS trong A.6: **FATF** (grey/black list), **FinCEN** (Mỹ), **EU tax haven blacklist**, **UN Security Council sanctions**.
2. Vì sao **DS đen tách riêng, điểm 7, khóa CIF ngay**, còn 9 DS mẫu chỉ chấm điểm 2–5 → hai mức chế tài khác nhau: chặn tuyệt đối vs giám sát tăng cường.
3. Vì sao thang điểm 7 / 5 / 4 / 3 / 2 chứ không phải boolean — dùng cho phân loại mức độ và hành động hạ nguồn.
4. Rule "trùng GTTT **hoặc** ≥2/4 trường **cùng 1 bản ghi**" — phân tích đánh đổi false positive / false negative, và **tại sao phải là "cùng 1 bản ghi"** (nếu trùng tên với người A và trùng SĐT với người B thì không tính).
5. Vì sao TH3 dừng ở lần trùng đầu tiên theo priority thay vì cộng dồn điểm nhiều DS.

**Không code.** Kết thúc phase: bạn giải thích lại được điểm 4 và 5 bằng lời của mình.

---

### Phase 1 — Data modeling & Schema

**Mục tiêu:** schema phải đủ để phục vụ **cả 4 kiểu tiêu chí (A.7)**, không chỉ 4 trường định danh.

**Nhóm bảng cần thiết:**

**Nhóm cấu hình danh sách:**
- `blacklist_entry` — DS đen: số GTTT, họ tên, ngày sinh, SĐT (+ trường chuẩn hóa).
- `watchlist_category` — 9 nhóm DS mẫu + các DS con: `priority`, `match_type` (K1/K2/K3/K4), `risk_level`, `risk_score`, `reason`, `sub_order` (giải Q2).
- `watchlist_entry` — bản ghi trong từng DS. Cấu trúc phải chứa được cả 4 kiểu: bản ghi định danh (K1) / mã quốc gia (K2) / nghề nghiệp (K3) / chức vụ (K4).
- `list_change_log` hoặc cột version — để trả lời "DS đen/DS mẫu có được điều chỉnh không" (giải Q6).

**Nhóm vận hành:**
- `pcrt_config` — cấu hình **x giờ chạy job hàng ngày** (A.5-B1).
- `customer_scan_queue` — DS KH cần đánh giá, trạng thái **Chờ xử lý / Đã xử lý**, `scan_batch_id`, `trigger_type` (T1/T2/T3a/T3b). Phải snapshot đủ dữ liệu KH cho cả 4 kiểu tiêu chí: GTTT, GTTT cũ, họ tên, ngày sinh, SĐT, **quốc gia, nghề nghiệp, chức vụ**, CIF.
- `customer_risk_result` — kết quả: CIF, mức rủi ro, điểm rủi ro, lý do, DS trùng, bản ghi trùng, thời điểm, trạng thái gửi Core (giải Q5).

**Bài tập thiết kế (mình sẽ hỏi, bạn trả lời trước khi mình đưa DDL):**
- Bảng `watchlist_entry` nên dùng **1 bảng chung nhiều cột nullable**, **EAV**, hay **bảng riêng cho từng match_type**? Trade-off là gì?
- Index nào cho `customer_scan_queue` để B3/B5 (quét bản ghi Chờ xử lý) không bị chậm khi có hàng triệu dòng?
- Chuẩn hóa dữ liệu (bỏ dấu, uppercase, chuẩn SĐT) nên làm lúc **ghi** (lưu cột `*_normalized`) hay lúc **so khớp**? Cái nào cho phép dùng index?

**Thực hành:** DDL PostgreSQL + JPA Entity cho toàn bộ bảng trên.

---

### Phase 2 — Matching Engine — TRỌNG TÂM

**Mục tiêu:** một engine so khớp duy nhất, phục vụ cả 3 flow, cả 4 kiểu tiêu chí, tách hoàn toàn khỏi DB (pure logic → unit test dễ).

**Nội dung:**

**2.1 — Chuẩn hóa dữ liệu (normalization)**
- Bỏ dấu tiếng Việt, uppercase, trim, chuẩn hóa khoảng trắng giữa các từ trong họ tên.
- Chuẩn hóa SĐT: `+84` / `84` / `0` → 1 dạng.
- Chuẩn hóa ngày sinh về `LocalDate`, xử lý trường hợp chỉ có năm sinh.
- Chuẩn hóa số GTTT: bỏ khoảng trắng, xử lý CMND 9 số vs CCCD 12 số.

**2.2 — K1: Matcher định danh cá nhân** *(DS đen, ưu tiên 1, 3, 7)*
- Rule: trùng **Số GTTT** → match; **hoặc** trùng **≥2 trong 4 trường** với **cùng 1 bản ghi** đích.
- Điểm mấu chốt: vòng lặp phải theo **từng bản ghi đích**, đếm số trường trùng trong bản ghi đó — không được gộp toàn bộ danh sách.
- Xử lý trường null: KH thiếu ngày sinh thì có tính là "trùng" không? (Không — null không bao giờ trùng.)
- Q3: có so khớp bằng **Số GTTT cũ** không.

**2.3 — K2/K3/K4: Matcher thuộc tính đơn** *(quốc gia / nghề nghiệp / chức vụ)*
- Đơn giản hơn K1: so khớp 1 trường với tập giá trị của DS.
- Q7/Q8: quy tắc so khớp (mã vs tên, exact vs contains).

**2.4 — Priority traversal (TH3)**
- Duyệt category theo `priority` tăng dần (1 → 9), trong cùng priority duyệt theo `sub_order`.
- **Early-stop**: trùng đầu tiên → dừng ngay, trả về điểm/mức/lý do của bản ghi đó.
- So sánh với TH1/TH2: chỉ 1 category (DS đen), không cần traversal.

**2.5 — Thiết kế API nội bộ**
```
interface CriteriaMatcher {          // K1, K2, K3, K4 mỗi loại 1 implement
    MatchType supports();
    Optional<MatchDetail> match(CustomerSnapshot c, List<WatchlistEntry> entries);
}

class RiskEvaluator {                // orchestrator
    RiskResult evaluateAgainstBlacklist(CustomerSnapshot c);        // TH1, TH2
    RiskResult evaluateAgainstWatchlists(CustomerSnapshot c);       // TH3, early-stop theo priority
}
```

**Thực hành + bộ test bắt buộc:**
- Trùng đúng 1 trường (tên) → **không** match.
- Trùng 2 trường nhưng **ở 2 bản ghi khác nhau** → **không** match.
- Trùng 2 trường **cùng 1 bản ghi** → match.
- Trùng GTTT, các trường khác đều khác → match.
- Tên có dấu vs không dấu, hoa/thường, thừa khoảng trắng → match.
- SĐT `0912...` vs `+84912...` → match.
- KH trùng **cả** ưu tiên 3 và ưu tiên 7 → kết quả phải là ưu tiên 3 (điểm 4), không phải 7.
- KH trùng cả DS chức vụ cao và trung bình (cùng ưu tiên 6) → theo quy tắc chốt ở Q2.
- KH không trùng gì → không sinh bản ghi rủi ro.

---

### Phase 3 — Batch Flow (TH1 + TH3) — TRỌNG TÂM

**Mục tiêu:** implement chính xác B1→B5 của A.3 và A.5, chịu được khối lượng "toàn bộ KH Active/Approved".

**3.1 — Đọc DB Core (B1)**
- TH1: toàn bộ KH cá nhân Active/Approved.
- TH3a: toàn bộ KH CN ví Active/Approved.
- TH3b: Active/Approved **AND** (`created` T-1 **OR** `update_time` T-1) **AND** điểm ≠ 7.
- Kỹ thuật: keyset pagination / cursor, **không** `OFFSET` lớn, không load hết vào memory.
- Phải lấy đủ trường cho cả 4 kiểu tiêu chí (kể cả quốc gia/nghề nghiệp/chức vụ).

**3.2 — Ghi hàng đợi (B2)**
- Batch insert vào `customer_scan_queue` trạng thái Chờ xử lý, gắn `scan_batch_id` + `trigger_type`.
- Snapshot dữ liệu KH tại thời điểm quét (để B3 không phải gọi lại Core).

**3.3 — Xử lý tuần tự (B3 + B5)**
- Vòng lặp: lấy bản ghi Chờ xử lý → gọi `RiskEvaluator` → cập nhật 'Đã xử lý' → nếu trùng thì sinh `customer_risk_result` + trả kết quả Core → kiểm tra còn bản ghi không → lặp.
- **Ranh giới transaction**: cập nhật trạng thái + sinh kết quả rủi ro phải trong 1 transaction; gọi Core phải **ngoài** transaction.
- **Resume**: job chết giữa chừng → chạy lại chỉ xử lý bản ghi còn Chờ xử lý (giải Q9).

**3.4 — Trigger**
- TH1: **event-driven** — kích hoạt khi DS đen được điều chỉnh (không phải cron).
- TH3: **cron động** đọc giờ `x` từ `pcrt_config`, không hardcode. Kỹ thuật: `SchedulingConfigurer` + `CronTrigger` đọc từ DB.
- TH3-B1: logic kiểm tra "DS mẫu có điều chỉnh trong ngày liền trước không" → chọn nhánh 3a hay 3b.

**Thực hành:** 2 job hoàn chỉnh (TH1 event-driven, TH3 cron động) dùng chung 1 `BatchScanProcessor`.

---

### Phase 4 — Realtime Flow (TH2)

**Mục tiêu:** API đồng bộ cho BE ví CN, trả kết quả nhanh.

**Nội dung:**
- Request DTO: bắt buộc CIF, số GTTT, họ tên, ngày sinh, SĐT; optional quốc gia, **số GTTT cũ**. Validate bằng Bean Validation.
- Vẫn phải **sinh bản ghi trong `customer_scan_queue`** (spec B2 nêu rõ) rồi mới so khớp → dùng chung code path với batch.
- Response về Core: **số CIF, mức rủi ro, điểm rủi ro, Lý do**.
- Nhánh không trùng → cập nhật 'Đã xử lý' → **kết thúc, không gọi Core**.
- Cân nhắc: gọi Core đồng bộ ngay trong request, hay trả 200 rồi gửi Core bất đồng bộ.

**Thực hành:** Controller + Service, tái dùng `RiskEvaluator`.

---

### Phase 5 — Tích hợp PCRT ↔ Core ví

**Mục tiêu:** kết quả rủi ro phải đến được Core, kể cả khi Core tạm thời lỗi; và **không** gây khóa CIF trùng lặp.

**Nội dung:**
- Contract gửi Core: CIF, mức rủi ro, điểm rủi ro, lý do (A.4-B3).
- Khác biệt hành vi Core: **TH1/TH2 → cập nhật + khóa CIF**; **TH3 → chỉ cập nhật** (chốt Q1 trước khi code).
- Retry + circuit breaker (Resilience4j) khi Core down.
- **Idempotency**: PCRT gửi lại sau timeout không được làm Core khóa CIF 2 lần → khóa idempotency theo `result_id`.
- Trạng thái gửi Core trong `customer_risk_result` (PENDING / SENT / FAILED) + job gửi lại.

**Thực hành:** Feign/RestClient + Resilience4j, test giả lập Core timeout.

---

### Phase 6 — Testing

- Unit test `CriteriaMatcher` (bộ case ở Phase 2).
- Integration test full 3 flow với Testcontainers PostgreSQL.
- Case bắt buộc: TH3 dừng đúng ở priority nhỏ nhất; TH1 resume sau khi job chết; TH2 không gọi Core khi không trùng; TH3b lọc đúng KH T-1 và điểm ≠ 7.

---

### Phase 7 — Scale & Production *(học sau)*

- Song song hóa batch (spec ghi "tuần tự" — cần chốt lại có được đổi không).
- Nhiều instance PCRT cùng chạy → claim-based (`UPDATE ... WHERE status='CXL'`, `FOR UPDATE SKIP LOCKED`).
- Kafka thay REST cho việc trả kết quả Core.
- Connection pool riêng cho job nền, tránh cạnh tranh traffic realtime.
- Monitoring: số KH quét/ngày, tỉ lệ trùng, thời gian hoàn thành batch.

---

## Cách sử dụng
Nói **"học Phase X"** để bắt đầu. Mặc định mình dạy theo hướng: giải thích khái niệm → đặt bài tập nhỏ → bạn thử → mình review. Muốn đi nhanh (xem code mẫu trực tiếp) thì nói rõ.

**Khuyến nghị:** chốt nhóm câu hỏi Q1–Q10 (mục A.8) với BA/nghiệp vụ trước khi vào Phase 1, vì chúng ảnh hưởng trực tiếp đến schema và matching logic.