# PCRT Lab — Roadmap học tập theo Phase

> **Project**: `pcrt-lab` — Rule engine phát hiện giao dịch nghi ngờ (AML/Fraud) cho ví điện tử, config-driven, chạy multi-instance.
>
> **Mục tiêu kép**: (1) implement toàn bộ 11 tiêu chí PCRT ở mức production-grade; (2) mỗi phase mở khóa một nhóm câu hỏi phỏng vấn senior — bạn build xong là kể được thành system design story.
>
> **Nhịp độ**: linh hoạt. Mỗi phase là một "đơn vị hoàn chỉnh" — làm xong mới sang phase sau. Không giới hạn thời gian, nhưng **không nhảy phase**.

---

## 0. Nguyên tắc học xuyên suốt

Mỗi phase đi theo đúng 5 bước, không bỏ bước nào:

| Bước | Việc làm | Tại sao |
|---|---|---|
| **1. READ** | Đọc lý thuyết của phase (30–60 phút), ghi lại 5 câu hỏi bạn chưa trả lời được | Tránh code trước khi hiểu |
| **2. DESIGN** | Viết **ADR ngắn** (1 trang): vấn đề, 2–3 phương án, chọn cái nào, đánh đổi gì | Đây chính là kỹ năng NAB test ở vòng manager |
| **3. BUILD** | Implement theo acceptance criteria của phase | — |
| **4. BREAK** | Viết test **cố tình phá**: concurrency, duplicate, restart giữa chừng, clock skew | Đây là thứ phân biệt senior với mid |
| **5. EXPLAIN** | Ghi âm/nói to 5 phút tiếng Anh: "I built X, the hard part was Y, I chose Z because…" | NAB vòng system design ~50–100% tiếng Anh |

**Quy tắc vàng**: kết thúc mỗi phase, bạn phải trả lời được toàn bộ mục *"Interview unlocked"* của phase đó **mà không nhìn code**. Nếu không → chưa qua phase.

---

## 1. Tech stack & scaffolding

```
Runtime      : Java 21, Spring Boot 3.5.x
Persistence  : PostgreSQL 16 + Flyway + Spring Data JPA (+ JdbcTemplate cho query nặng)
Cache/Window : Redis 7 (Sorted Set + Lua)
Messaging    : Kafka (Redpanda cho local — nhẹ hơn)
Batch        : Spring Batch + ShedLock (distributed scheduler lock)
Resilience   : Resilience4j
Observability: Micrometer + Prometheus + Grafana + Loki (hoặc file JSON log)
Test         : JUnit 5 + Testcontainers + Awaitility + jqwik (property-based)
Load test    : k6
CI           : GitHub Actions
Local infra  : Docker Compose
```

### Cấu trúc repo đề xuất (modular monolith — dễ tách microservice sau)

```
pcrt-lab/
├── docker/                      # compose, grafana dashboards, k6 scripts
├── pcrt-common/                 # shared types: Money, TxType, RuleCode, error model
├── pcrt-ingest/                 # nhận event từ ví (Kafka consumer + REST fallback)
├── pcrt-engine/                 # ★ rule engine core (domain thuần, không framework)
│   ├── domain/                  #   Rule, RuleContext, RuleResult, Window, Score
│   ├── realtime/                #   Job 1
│   ├── daily/                   #   Job 2, 3
│   └── device/                  #   Job 4
├── pcrt-persistence/            # JPA entities, repositories, Flyway migrations
├── pcrt-api/                    # REST + SSE cho màn hình giám sát
├── pcrt-simulator/              # sinh dữ liệu giả lập ví (kịch bản sạch + kịch bản gian lận)
└── pcrt-app/                    # Spring Boot main, wiring, config
```

> **Lý do tách `pcrt-engine` không phụ thuộc Spring**: rule logic test được bằng unit test thuần, chạy trong mili-giây, không cần container. Đây là điểm bạn sẽ được hỏi ở vòng architecture: *"how do you keep business logic testable?"*

---

## 2. Bản đồ Phase → Kỹ năng → Interview

| Phase | Nội dung | Category interview được cover |
|---|---|---|
| 0 | Scaffolding & môi trường | DevOps, Docker |
| 1 | Ingestion & idempotency | Microservices, Kafka, DB |
| 2 | Job 1 realtime velocity | **System Design (rate limiter)**, Redis, Concurrency |
| 3 | Rule engine config-driven | **Java core / design patterns**, Effective Java |
| 4 | Job 2 daily batch | Spring Batch, DSA (two pointers), SQL |
| 5 | Job 3 money-flow | **SQL nâng cao**, index, EXPLAIN |
| 6 | Job 4 device/IP | SQL cardinality, data modeling |
| 7 | Scoring + API + SSE | Spring MVC, REST design, SSE, locking |
| 8 | Resilience & outbox | **Microservices patterns**, Resilience4j |
| 9 | Observability & performance | JVM/GC, HikariCP, Prometheus |
| 10 | Testing & CI | Testing strategy, CI/CD |
| 11 | Security hardening | **Security (OWASP, PCI-DSS)** |
| 12 | Đóng gói thành interview asset | **System Design mock + STAR** |

---

# PHASE 0 — Nền móng

### Mục tiêu học
Dựng được môi trường local reproducible; hiểu Spring Boot khởi động thế nào; Testcontainers hoạt động ra sao.

### Build
- `docker-compose.yml`: Postgres, Redis, Redpanda, Redpanda Console, Prometheus, Grafana
- Multi-module Maven project theo cấu trúc trên
- Flyway migration đầu tiên: `V1__init_schema.sql`
- 1 integration test dùng Testcontainers khởi động Postgres thật
- Spring Boot Actuator + health check tùy chỉnh cho Redis/Kafka

### Khái niệm cốt lõi
- Multi-stage Dockerfile, layer caching
- Spring Boot auto-configuration: `@Conditional`, `AutoConfiguration.imports`
- Property precedence: `application.yml` → profile → env var → command-line
- Testcontainers reuse mode (tăng tốc test)

### Interview unlocked
1. Auto-configuration hoạt động thế nào? Làm sao debug khi bean không được tạo? (`--debug` → `ConditionEvaluationReport`)
2. Bean của bạn và bean auto-config trùng nhau thì cái nào thắng? Vì sao?
3. Vì sao multi-stage build lại quan trọng với image Java?
4. JVM chạy trong container có gì khác? (`MaxRAMPercentage`, container-aware since JDK 10, nguy cơ default Serial GC khi <2 CPU)

### Acceptance
- [ ] `docker compose up -d` → tất cả service healthy
- [ ] `mvn verify` xanh, có ít nhất 1 test dùng Testcontainers
- [ ] `/actuator/health` trả `UP` với chi tiết từng component

### Bẫy thường gặp
- Hardcode `localhost` vào config → không chạy được trong container. Dùng service name.
- Quên `-XX:MaxRAMPercentage` → JVM lấy 1/4 RAM host thay vì RAM container.

---

# PHASE 1 — Ingestion & Idempotency

### Mục tiêu học
Nhận event từ ví một cách **exactly-once về mặt hiệu ứng** (effectively-once), dù Kafka chỉ đảm bảo at-least-once.

### Build
- `pcrt-simulator`: sinh transaction event + login event, publish lên Kafka
    - Topic `wallet.transaction.succeeded`, partition key = `account_id` (đảm bảo thứ tự theo account)
    - Topic `wallet.login.succeeded`
- Consumer trong `pcrt-ingest`:
    - Ghi vào `pcrt_tx_inbox` với `ON CONFLICT (transaction_id) DO NOTHING RETURNING *`
    - Chỉ khi RETURNING có row mới → đẩy xuống engine
- REST fallback endpoint `POST /internal/tx` với header `Idempotency-Key`
- Kịch bản simulator có sẵn: `clean`, `job1_velocity`, `job2_fast_inout`, `job3_fan_in`, `job4_shared_ip`

### Khái niệm cốt lõi
- **Partition key = account_id** → tại sao? (ordering guarantee trong 1 partition; các rule đều theo account)
- At-least-once + idempotent consumer = effectively-once
- Manual ack vs auto-commit offset; commit **sau** khi xử lý xong
- Consumer group rebalance và ảnh hưởng tới xử lý dở dang
- `ON CONFLICT DO NOTHING RETURNING` — vì sao tốt hơn `SELECT` rồi `INSERT` (race condition)

### Interview unlocked
1. Kafka đảm bảo exactly-once không? Bạn làm gì để đạt effectively-once? *(đây chính là câu hỏi đã loại 1 ứng viên "5 năm kinh nghiệm" trong bài Viblo ở roadmap của bạn)*
2. Vì sao chọn `account_id` làm partition key? Rủi ro là gì? (hot partition khi 1 account giao dịch cực nhiều)
3. `SELECT` rồi `INSERT` sai ở đâu dưới concurrency? Vì sao `ON CONFLICT` đúng?
4. Consumer crash sau khi xử lý nhưng trước khi commit offset → chuyện gì xảy ra?

### Acceptance
- [ ] Gửi cùng 1 event 5 lần → đúng 1 row trong inbox, engine được gọi đúng 1 lần
- [ ] Test concurrency: 10 thread cùng gửi 1 `transaction_id` → 1 row
- [ ] Kill consumer giữa chừng, restart → không mất và không trùng event

### Bẫy thường gặp
- Auto-commit offset → mất event khi crash.
- Dedup bằng `Set` trong memory → sai khi chạy nhiều instance (đây là bài học bạn phải kể được).

---

# PHASE 2 — Job 1 Realtime Velocity ★ (phase quan trọng nhất)

### Mục tiêu học
Đây là **trái tim của project và là câu chuyện system design mạnh nhất của bạn**. Cùng họ với bài "Design a Rate Limiter" mà NAB/bank hay hỏi.

### Build
Implement đầy đủ Job 1 theo spec, gồm cả các lưu ý khó:

1. **Sliding window bằng Redis Sorted Set**
   ```
   key    = pcrt:vel:{account_id}
   score  = occurred_at (epoch millis)
   member = transaction_id
   ```
    - Thêm: `ZADD`
    - Đếm trong X phút: `ZCOUNT key (now-X) now`
    - Dọn hết hạn: `ZREMRANGEBYSCORE key -inf (now-X)`

2. **Toàn bộ thao tác gói trong 1 Lua script** (atomic, chống race giữa nhiều instance)

3. **Continuation logic**: sau khi vi phạm & cộng điểm → cửa sổ tiếp theo bắt đầu từ GD **ngay sau** GD cuối cùng vi phạm

4. **Chống đếm trùng**: GD đã tính vào cảnh báo → không tính lại (đánh dấu `counted_in_alert_id`)

5. **Repeat counting**: đếm số lần vi phạm trong ngày (Y=1), đủ N=3 → cảnh báo + cộng điểm

6. So sánh **3 cách implement** và viết ADR chọn 1:
    - Counter thủ công (`INCR` + `DECR` khi TTL hết) — đúng như spec BA mô tả
    - Sorted Set (khuyến nghị)
    - DB table + index trên `expire_at`

### Khái niệm cốt lõi
- 4 thuật toán rate limiting: fixed window / sliding window log / sliding window counter / token bucket — ưu nhược
- **Vì sao Lua script atomic**: Redis single-threaded, script chạy trọn vẹn không bị chen ngang
- Vì sao counter thủ công bị **drift** (đúng điểm 6.1 trong tài liệu rule của bạn)
- TTL vs lazy expiration trong Redis
- Clock skew giữa các instance → dùng `occurred_at` từ event hay `TIME` của Redis?

### Interview unlocked
1. Thiết kế rate limiter phân tán. So sánh 4 thuật toán, chọn cái nào cho case nào?
2. Vì sao cần Lua script? Không dùng thì hỏng thế nào? (đưa ra được kịch bản race cụ thể)
3. Sorted Set tốn bộ nhớ hơn counter — bạn đánh đổi thế nào? Khi nào chọn counter?
4. Hệ thống chạy 5 pod, làm sao đảm bảo đếm không sai?
5. Clock skew ảnh hưởng ra sao? Bạn xử lý thế nào? *(liên hệ được với bài học OTP `expired_at` server-issued của bạn)*
6. Nếu Redis chết thì sao? Fail-open hay fail-closed? Với AML thì chọn gì? (gợi ý: khác với rate limiter thông thường)

### Acceptance
- [ ] Test: 60 GD trong 5 phút → gắn cờ; 49 GD → không
- [ ] Test concurrency: 4 instance × 500 event đồng thời → count chính xác tuyệt đối, alert sinh đúng 1 lần
- [ ] Test continuation: vi phạm lần 1 xong, GD tiếp theo bắt đầu cửa sổ mới đúng vị trí
- [ ] Test không đếm trùng: GD đã vào alert không xuất hiện trong alert kế
- [ ] Benchmark: p99 < 5ms cho 1 lần evaluate

### Bẫy thường gặp
- Dùng `System.currentTimeMillis()` của từng pod → skew. Dùng timestamp trong event.
- `ZCOUNT` rồi mới `ZADD` (hoặc ngược lại) ở 2 lệnh riêng → race. Phải trong Lua.
- Quên `EXPIRE` cho key → memory leak với account ngừng hoạt động.

---

# PHASE 3 — Rule Engine config-driven ★

### Mục tiêu học
Biến 11 tiêu chí rời rạc thành **một engine có kiến trúc**. Đây là phase thể hiện năng lực thiết kế, không phải năng lực code.

### Build
1. **SPI của rule** (domain thuần, không Spring):
   ```java
   public interface Rule<C extends RuleContext> {
       RuleCode code();
       RuleScope scope();          // ACCOUNT | CIF | DEVICE | IP
       RuleTrigger trigger();      // REALTIME | DAILY
       RuleOutcome evaluate(C ctx, RuleParams params);
   }
   ```
2. **`RuleParams`** đọc từ bảng `pcrt_rule_config` có **versioning + effective_from/to**
3. **`RuleRegistry`**: tự động discover mọi implement (Spring `ObjectProvider<List<Rule>>`)
4. **`RuleOutcome`** thống nhất: `NO_MATCH | FLAGGED | ALERTED`, kèm `metrics` (JSONB) + `txRefs`
5. **Pipeline chung** cho pattern "flag → đếm lặp N trong Y ngày → alert" — 7/11 tiêu chí dùng chung pattern này, **không được copy-paste 7 lần**
6. Config hot-reload: đổi `T` từ 50 → 10 trong DB, không redeploy, rule đổi hành vi
7. Audit config: ai đổi, khi nào, giá trị cũ/mới (bắt buộc với hệ thống AML)

### Khái niệm cốt lõi
- Strategy pattern + Registry pattern
- Effective Java: favor composition over inheritance; program to interfaces; static factory; builder cho `RuleParams`
- Sealed interface cho `RuleOutcome` + pattern matching `switch` (Java 21)
- Template Method vs Strategy — khi nào dùng cái nào cho pipeline "flag → repeat → alert"
- Tách domain khỏi framework (hexagonal / ports & adapters)

### Interview unlocked
1. Thiết kế hệ thống cho phép thêm rule mới **không sửa code cũ** — giải thích bằng Open/Closed Principle.
2. Vì sao tách domain khỏi Spring? Lợi ích cụ thể đo được là gì?
3. Config đổi runtime — làm sao đảm bảo tất cả pod thấy giá trị mới? Nhất quán ra sao? (cache + TTL vs pub/sub invalidation)
4. Sealed class/interface giải quyết vấn đề gì mà enum không giải quyết được?
5. Bạn refactor 7 rule trùng logic thành 1 pipeline thế nào? (câu chuyện STAR về technical leadership)

### Acceptance
- [ ] Thêm 1 rule mới chỉ cần: tạo 1 class + 1 dòng insert config, **không sửa file nào khác**
- [ ] Đổi tham số trong DB → có hiệu lực ≤ 30s, không restart
- [ ] Unit test rule chạy **không cần Testcontainers**, toàn bộ suite < 2s

### Bẫy thường gặp
- Nhét config vào `application.yml` → không đổi runtime được, không audit được. AML bắt buộc audit.
- Rule phụ thuộc trực tiếp vào JPA entity → không test nhanh được. Dùng DTO domain riêng.

---

# PHASE 4 — Job 2: Daily Batch

### Mục tiêu học
Xử lý batch khối lượng lớn, chạy multi-instance an toàn, và **thuật toán cửa sổ trượt** (đây là DSA thật, dùng được cho vòng coding).

### Build
1. **Job 2 TH1** — đếm GD/ngày ≥ 480 (dễ, làm trước để có khung)
2. **Job 2 TH2** — nạp vào rút ra nhanh: đây là bài **two pointers / prefix sum** kinh điển
    - Tìm cửa sổ nhỏ nhất mà tổng nạp tích lũy ≥ 50tr
    - Nếu cửa sổ > 10 phút → trượt sang GD kế tiếp, lặp lại
    - Trong cửa sổ đó tìm GD rút/chuyển, tính G1
3. **Job 2 TH3** — nạp nhỏ nhiều lần → chuyển lớn (dùng pipeline flag/repeat từ Phase 3)
4. **Spring Batch**: reader (keyset pagination) → processor (rule) → writer (chunk)
5. **ShedLock**: đảm bảo chỉ 1 instance chạy job
6. **Idempotent rerun**: chạy lại job cho cùng ngày t → không cộng điểm 2 lần (`UNIQUE(rule_code, subject, evaluation_date)`)

### Khái niệm cốt lõi
- Two pointers / sliding window trên mảng đã sắp xếp theo thời gian — **O(n) thay vì O(n²)**
- Keyset pagination (`WHERE id > :lastId ORDER BY id LIMIT 1000`) vs OFFSET — vì sao OFFSET chậm dần
- Spring Batch chunk-oriented processing, restartability, `JobRepository`
- ShedLock hoạt động thế nào (lock row trong DB, `lockAtMostFor` chống deadlock khi pod chết)
- Vì sao **không** dùng `@Scheduled` trần khi chạy nhiều replica

### Interview unlocked
1. Job chạy trên 5 pod, làm sao đảm bảo chạy đúng 1 lần? Nếu pod cầm lock bị kill thì sao?
2. Vì sao `OFFSET 1000000` chậm? Thay bằng gì?
3. Bạn xử lý 10 triệu giao dịch/ngày thế nào để không OOM? (streaming/cursor, chunk, không load hết vào List)
4. Giải thuật tìm cửa sổ thời gian — độ phức tạp? Tối ưu từ O(n²) xuống O(n) thế nào?
5. Job chạy lại sau khi fail giữa chừng — làm sao không double-count?

### Acceptance
- [ ] Sinh 1 triệu GD trong 1 ngày → job chạy xong, heap không vượt 512MB
- [ ] Chạy job 3 lần cho cùng ngày → số alert không đổi
- [ ] Kill pod giữa lúc chạy → pod khác tiếp quản hoặc job restart sạch
- [ ] Unit test thuật toán cửa sổ với ≥ 10 fixture (bao gồm edge case: đúng 10 phút, GD cùng millisecond)

### Bẫy thường gặp
- `findAll()` rồi lọc trong Java → OOM. Lọc ở SQL.
- `@Scheduled` + 3 replica → job chạy 3 lần, cộng điểm 3 lần.
- Quên xử lý biên `< X` hay `≤ X` → sai lệch kết quả AML (rủi ro compliance thật).

---

# PHASE 5 — Job 3: Money Flow (SQL nâng cao)

### Mục tiêu học
Đây là phase **SQL nặng nhất** — trực tiếp phục vụ vòng phỏng vấn Database của cả NAB lẫn ngân hàng VN.

### Build
1. **Job 3 TH1** — nhiều ví nhỏ dồn về 1 ví trong thời gian ngắn
    - Cần đếm **distinct ví nguồn** trong cửa sổ trượt → `COUNT(DISTINCT)` trong window function không hỗ trợ trực tiếp → phải nghĩ cách
2. **Job 3 TH2** — nhận tiền lớn bất thường so với baseline 10 ngày
    - Window function: `SUM(amount) OVER (PARTITION BY account_id ORDER BY tx_date ROWS BETWEEN 10 PRECEDING AND 1 PRECEDING)`
    - Xử lý baseline = 0 (điểm 6.5 trong tài liệu rule)
3. Thiết kế index cho từng query, chạy `EXPLAIN (ANALYZE, BUFFERS)` và **ghi lại plan trước/sau**
4. Bảng aggregate `pcrt_daily_account_summary` (denormalize) — so sánh hiệu năng có/không

### Khái niệm cốt lõi
- Window functions: `SUM/LAG/LEAD/ROW_NUMBER OVER (PARTITION BY … ORDER BY … ROWS BETWEEN …)`
- CTE và `MATERIALIZED` / `NOT MATERIALIZED` (PG12+)
- Composite index — thứ tự cột quyết định thế nào; covering index (`INCLUDE`)
- Đọc EXPLAIN: Seq Scan vs Index Scan vs Bitmap Heap Scan; `rows` estimate lệch → thống kê cũ → `ANALYZE`
- Partitioning theo ngày cho bảng transaction (`PARTITION BY RANGE (occurred_at)`)
- MVCC: vì sao bảng ghi nhiều bị bloat, VACUUM làm gì

### Interview unlocked
1. Query chậm — quy trình debug của bạn? (EXPLAIN ANALYZE → tìm node đắt nhất → index/rewrite)
2. Index composite `(a, b)` dùng được cho `WHERE b = ?` không? Vì sao?
3. Khi nào denormalize? Đánh đổi gì? (bạn có số liệu thật từ project)
4. Partitioning giúp gì? Khi nào phản tác dụng?
5. MVCC hoạt động thế nào? Vì sao `UPDATE` nhiều làm bảng phình?
6. `COUNT(DISTINCT)` trên bảng lớn tốn kém — có cách nào rẻ hơn? (HyperLogLog, aggregate table)

### Acceptance
- [ ] Mọi query của job có `EXPLAIN` dùng Index Scan (không Seq Scan trên bảng > 100k row)
- [ ] Có file `docs/query-tuning.md` ghi plan trước/sau + thời gian, tối thiểu 3 query
- [ ] Test rule TH2 với account mới (baseline = 0) → không false positive

### Bẫy thường gặp
- Index trên cột có cardinality thấp (`status`) một mình → vô dụng. Phải composite.
- `WHERE DATE(occurred_at) = '2026-08-04'` → không dùng được index. Dùng range `>= ... AND < ...`.

---

# PHASE 6 — Job 4: Device / IP

### Mục tiêu học
Data modeling cho quan hệ nhiều-nhiều (user ↔ device ↔ IP) và các truy vấn cardinality.

### Build
1. Bảng `pcrt_device_activity` + index phù hợp (đã có DDL sơ bộ trong tài liệu rule)
2. **TH1** — 1 IP/device ≥ 20 user (chấm điểm theo Device/IP, khác các rule kia)
3. **TH2.1 / TH2.2** — đa quốc gia; **xử lý vấn đề chồng lấn** đã nêu ở điểm 6.6 (chống cộng 2 điểm cho cùng hành vi)
4. **TH3** — tỉ lệ GD từ IP nước ngoài ≥ 90% trong 30 ngày
5. **TH4** — ≥ 5 device, lặp ≥ 5 lần trong 30 ngày
6. GeoIP lookup: gọi service ngoài (mock) → chuẩn bị sẵn cho Phase 8 (Resilience4j)
7. Xử lý false positive: whitelist IP, CGNAT, VPN

### Khái niệm cốt lõi
- Kiểu `INET` / `CIDR` của PostgreSQL và index GiST
- Anti-pattern: query "tìm mọi IP có ≥ 20 user" trên bảng raw mỗi ngày → phải có aggregate table
- Deduplication alert khi 2 rule chồng lấn — thiết kế `alert_dedup_key`

### Interview unlocked
1. Model quan hệ nhiều-nhiều với thời gian (temporal many-to-many) thế nào?
2. Rule chồng lấn sinh alert trùng — bạn giải quyết ra sao? (đây là judgment call, interviewer thích)
3. False positive trong hệ thống AML tốn kém thế nào? Bạn cân bằng precision/recall ra sao?
4. GeoIP không đáng tin (VPN, CGNAT) — thiết kế chịu được dữ liệu bẩn thế nào?

### Acceptance
- [ ] Fixture: 1 IP dùng bởi 25 user → gắn cờ đúng; 19 user → không
- [ ] User thỏa cả TH2.1 và TH2.2 → chỉ cộng điểm theo rule đã thống nhất, có test chứng minh
- [ ] Có ADR về xử lý chồng lấn rule

---

# PHASE 7 — Scoring, Alert Lifecycle & API

### Mục tiêu học
Thiết kế REST API cấp senior + SSE realtime (nối tiếp mảng bạn đang học) + optimistic locking.

### Build
1. **Điểm rủi ro tích lũy**: cộng điểm, ngưỡng escalate, có decay theo thời gian không (ADR)
2. **Alert lifecycle**: `NEW → ASSIGNED → INVESTIGATING → CLOSED_TRUE_POSITIVE | CLOSED_FALSE_POSITIVE`
    - State machine: `UPDATE ... WHERE status = :from` (pattern bạn đã dùng ở cash-out)
    - Optimistic locking `@Version` cho việc gán case cho analyst
3. **REST API**:
    - `GET /alerts` — keyset pagination, filter theo rule/score/date
    - `GET /alerts/{id}` — chi tiết + danh sách `transaction_id_refer`
    - `POST /alerts/{id}/transitions` — có `Idempotency-Key`
    - `GET/PUT /rule-configs` — CRUD tham số, có audit
    - `@ControllerAdvice` + error model chuẩn (RFC 7807 Problem Details)
    - Bean Validation `@Valid`
    - OpenAPI/Swagger
4. **SSE**: `GET /alerts/stream` — đẩy alert mới realtime cho màn hình giám sát
    - Reconnect với `Last-Event-ID`
    - Multi-instance: dùng Redis Pub/Sub fan-out

### Khái niệm cốt lõi
- Optimistic vs pessimistic locking — khi nào dùng cái nào
- Idempotency cho write API (khác idempotency của consumer)
- SSE vs WebSocket vs long polling; SSE scaling qua nhiều pod
- `@Transactional` boundaries: gọi API ngoài **ngoài** transaction

### Interview unlocked
1. Hai analyst cùng nhận 1 case — bạn xử lý thế nào? Optimistic hay pessimistic? Vì sao?
2. `@Transactional` — kể 4 cái bẫy kinh điển (self-invocation, checked exception, private method, catch nuốt exception)
3. SSE vs WebSocket — chọn gì cho dashboard alert? Vì sao?
4. SSE khi có 5 pod và client kết vào pod A, alert sinh ở pod B → làm sao?
5. Thiết kế API idempotent cho thao tác POST — cần lưu gì, TTL bao lâu?

### Acceptance
- [ ] 2 request đồng thời gán cùng 1 case → 1 thành công, 1 nhận 409
- [ ] Gửi cùng `Idempotency-Key` 3 lần → 1 lần thực thi, 3 lần cùng response
- [ ] SSE: client kết pod A, alert sinh ở pod B → client vẫn nhận được
- [ ] Ngắt mạng client → reconnect với `Last-Event-ID` → không mất alert

---

# PHASE 8 — Resilience & Outbox

### Mục tiêu học
Các pattern microservices mà NAB hỏi rất sâu ở vòng manager (Saga, outbox, event-driven).

### Build
1. **Transactional Outbox**: khi sinh alert → ghi `pcrt_alert` + `pcrt_outbox` trong **1 transaction**; relay đọc outbox bằng `FOR UPDATE SKIP LOCKED` và publish Kafka
2. **Notification consumer**: gửi email/webhook cho compliance team
3. **Resilience4j** cho GeoIP service ngoài: CircuitBreaker + Retry (backoff + jitter) + TimeLimiter + Bulkhead
    - Nắm **thứ tự aspect cố định**: Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead
4. **DLQ** cho message xử lý fail sau N lần retry + endpoint replay DLQ
5. Fallback khi Redis chết: rule realtime chuyển sang degraded mode (ADR: fail-open hay fail-closed cho AML?)

### Khái niệm cốt lõi
- Dual-write problem — vì sao ghi DB rồi publish Kafka là **sai**
- `FOR UPDATE SKIP LOCKED` — nhiều relay chạy song song không giẫm chân nhau
- Circuit breaker states: CLOSED → OPEN → HALF_OPEN
- Retry storm — vì sao retry phải đi kèm circuit breaker và jitter
- Retry chỉ an toàn khi thao tác idempotent
- Saga vs Outbox vs CQRS vs Event Sourcing — **khung quyết định**

### Interview unlocked
1. Dual-write problem là gì? Outbox giải quyết thế nào? Nhược điểm của outbox?
2. Vì sao 2PC là anti-pattern giữa các microservice?
3. `SKIP LOCKED` giải quyết vấn đề gì? Không có nó thì sao?
4. Retry + circuit breaker — thứ tự bọc nhau thế nào và vì sao thứ tự đó quan trọng?
5. Khi nào dùng Saga, khi nào Outbox, khi nào CQRS? (đưa ra khung quyết định 1 câu cho mỗi cái)
6. Redis chết — AML nên fail-open hay fail-closed? Bảo vệ lựa chọn của bạn.

### Acceptance
- [ ] Kill Kafka → alert vẫn ghi được vào DB; Kafka lên lại → outbox tự publish hết
- [ ] 3 relay instance chạy song song → không message nào publish 2 lần
- [ ] GeoIP service trả 500 liên tục → circuit mở, không retry storm, có fallback
- [ ] Message fail 3 lần → vào DLQ, replay được

---

# PHASE 9 — Observability & Performance

### Mục tiêu học
Tư duy "own it end-to-end" — thứ NAB đánh giá cao ở senior.

### Build
1. **Micrometer metrics**: `pcrt.rule.evaluation.duration` (timer, tag=rule_code), `pcrt.alert.created` (counter), `pcrt.outbox.lag` (gauge), HikariCP pool metrics, Resilience4j metrics
2. **Grafana dashboard**: latency p50/p95/p99 theo rule, alert rate, outbox lag, GC pause, pool usage
3. **Structured logging** JSON + `correlationId` xuyên suốt (MDC), propagate qua Kafka header
4. **Distributed tracing** (Micrometer Tracing + Zipkin/Tempo)
5. **Load test k6**: 5.000 tx/s vào endpoint realtime, đo p99
6. **Tuning thực tế**:
    - HikariCP: bắt đầu từ `(cores × 2)`, đo, chứng minh vì sao pool lớn hơn **tệ hơn**
    - JVM: so sánh G1 vs ZGC ở heap 2GB, đo pause time
    - `max-lifetime` < DB timeout

### Khái niệm cốt lõi
- 3 trụ observability: metrics, logs, traces — mỗi cái trả lời câu hỏi gì
- RED method (Rate, Errors, Duration) vs USE method
- Vì sao connection pool bão hòa thường là vấn đề slow query, không phải pool size
- Container-aware JVM: `MaxRAMPercentage`, tại sao <2 CPU có thể rơi về SerialGC

### Interview unlocked
1. Production chậm — quy trình điều tra của bạn từ metric → trace → log?
2. Pool 100 connection có nhanh hơn 20 không? Giải thích bằng cơ chế.
3. GC pause 2 giây — bạn debug thế nào? Chọn collector nào cho service này?
4. Correlation ID đi qua Kafka thế nào?
5. Bạn đo được gì trước/sau tuning? (có số liệu thật — đây là điểm cộng lớn)

### Acceptance
- [ ] Dashboard Grafana có đủ 6 panel nêu trên
- [ ] Có `docs/perf-report.md`: baseline → thay đổi → kết quả, tối thiểu 3 thí nghiệm có số liệu
- [ ] 1 request trace được xuyên từ ingest → rule → alert → outbox → notification

---

# PHASE 10 — Testing Strategy & CI

### Mục tiêu học
Chứng minh được chất lượng, không chỉ nói.

### Build
1. **Test pyramid**:
    - Unit: rule logic thuần (nhanh, nhiều)
    - Slice: `@DataJpaTest`, `@WebMvcTest`
    - Integration: Testcontainers (Postgres + Redis + Kafka)
    - Contract: schema của event ví ↔ PCRT
2. **Property-based test (jqwik)** cho thuật toán cửa sổ trượt — sinh ngẫu nhiên chuỗi GD, kiểm tra invariant (VD: số GD đếm được luôn ≤ tổng GD trong cửa sổ)
3. **Concurrency test** chuẩn cho Phase 2 và 7
4. **Mutation testing (PIT)** trên module `pcrt-engine` — mục tiêu mutation score > 70%
5. **GitHub Actions**: build → unit → integration (Testcontainers) → PIT → build image → (mock) deploy
6. Test data builder pattern cho fixture

### Khái niệm cốt lõi
- Coverage vs mutation score — vì sao coverage 90% vẫn có thể vô nghĩa
- Property-based testing: bạn kiểm tra **tính chất**, không phải ví dụ cụ thể
- Flaky test do thời gian → dùng `Clock` inject được, không `Instant.now()` trực tiếp

### Interview unlocked
1. Chiến lược test của bạn cho hệ thống này? Tỉ lệ từng tầng?
2. Coverage 95% mà vẫn có bug — vì sao? Mutation testing giúp gì?
3. Làm sao test logic phụ thuộc thời gian mà không `Thread.sleep`?
4. Test concurrency thế nào cho đáng tin (không flaky)?

### Acceptance
- [ ] Toàn bộ unit test < 5s; integration test < 3 phút
- [ ] Mutation score `pcrt-engine` > 70%
- [ ] CI xanh, chạy tự động mỗi PR
- [ ] Không còn `Instant.now()` trực tiếp trong domain — đều qua `Clock`

---

# PHASE 11 — Security Hardening

### Mục tiêu học
Cover Category 7 trong roadmap phỏng vấn — bắt buộc với ngân hàng.

### Build
1. **Spring Security**: JWT resource server, role `ANALYST` / `SUPERVISOR` / `ADMIN`
2. **Method security** `@PreAuthorize` — chỉ SUPERVISOR đóng được case true-positive; chỉ ADMIN sửa được rule config
3. **BOLA prevention**: analyst chỉ xem được case được gán cho mình → kiểm tra ở **object level**, không chỉ function level
4. **Audit log bất biến**: mọi thay đổi config + mọi transition alert (append-only, không UPDATE/DELETE)
5. **PII masking** trong log: account number, CIF, số tiền lớn
6. **Mã hóa at-rest** cho cột nhạy cảm (pgcrypto hoặc app-level)
7. Rate limit + input validation trên API
8. Secrets qua env/Vault, không hardcode

### Khái niệm cốt lõi
- OWASP Top 10 + API Top 10 (BOLA đứng đầu)
- JWT: base64 chứ không mã hóa → không nhét PII; validate signature mỗi request
- `SecurityContext` là ThreadLocal → **không** propagate sang `@Async`/virtual thread
- PCI-DSS Req 6, Nghị định 13/2023 (Bảo vệ dữ liệu cá nhân) và 53/2022 (An ninh mạng)

### Interview unlocked
1. BOLA là gì? Cho ví dụ trong chính hệ thống của bạn và cách bạn chặn.
2. JWT nên chứa gì và không chứa gì? Revoke thế nào?
3. `SecurityContext` mất khi sang `@Async` — vì sao và fix thế nào?
4. Hệ thống AML cần audit trail thế nào để đứng vững trước thanh tra?

### Acceptance
- [ ] Analyst A gọi `GET /alerts/{id}` của analyst B → 403, có test
- [ ] Log không chứa số tài khoản đầy đủ (test assert)
- [ ] Mọi thay đổi config có bản ghi audit không xóa được

---

# PHASE 12 — Đóng gói thành tài sản phỏng vấn

### Mục tiêu học
Chuyển project thành **câu chuyện kể được trong 45 phút bằng tiếng Anh**.

### Build
1. **README** cấp senior: kiến trúc, quyết định thiết kế, cách chạy, số liệu hiệu năng, giới hạn đã biết
2. **Sơ đồ**: C4 Context + Container, sequence diagram cho Job 1, state machine của alert
3. **Tập ADR** (10–12 ADR bạn đã viết qua các phase) — đây là thứ interviewer NAB đọc và hỏi sâu
4. **One-pager system design** cho chính project, theo khung: requirements → API → data model → idempotency → consistency → failure modes → scale
5. **6–8 câu chuyện STAR** rút từ project, map vào 5 giá trị NAB:
   | Story | Giá trị NAB |
   |---|---|
   | Phát hiện counter drift, refactor sang Sorted Set | Being bold |
   | Refactor 7 rule trùng thành 1 pipeline | Winning together |
   | Phát hiện lỗi spec `G1 ≥ 80% G` và chủ động hỏi BA | Doing the right thing |
   | Cân bằng false positive vs recall cho compliance team | Passion for customers |
   | Mentor fresher qua chính codebase này | Respecting people |
6. **3 mock design 45 phút bằng tiếng Anh** (tự quay lại): payment system, digital wallet, rate limiter/fraud detection

### Acceptance
- [ ] Trình bày project 15 phút tiếng Anh **không nhìn note**
- [ ] Trả lời được toàn bộ mục "Interview unlocked" của 12 phase mà không mở code
- [ ] Repo public, README có sơ đồ, có số liệu hiệu năng thật

---

## 3. Tự đánh giá — checkpoint sau mỗi phase

Chấm 1–5 cho từng mục, **< 4 thì chưa qua phase**:

| # | Tiêu chí |
|---|---|
| 1 | Tôi giải thích được **vì sao** chọn cách này thay vì cách khác, không chỉ mô tả đã làm gì |
| 2 | Tôi biết cách này **hỏng ở đâu** và trong điều kiện nào |
| 3 | Tôi có test chứng minh nó đúng dưới concurrency / failure |
| 4 | Tôi trình bày được bằng **tiếng Anh** trong 5 phút |
| 5 | Tôi liên hệ được với ít nhất 1 câu hỏi phỏng vấn thật |

---

## 4. Thứ tự ưu tiên nếu cần cắt ngắn

Nếu có lúc phải rút gọn, giữ theo thứ tự này (giá trị phỏng vấn giảm dần):

**Không được bỏ**: Phase 2 (realtime velocity) → Phase 3 (rule engine) → Phase 8 (outbox/resilience) → Phase 5 (SQL) → Phase 1 (idempotency)

**Có thể làm nhẹ**: Phase 6 (device/IP), Phase 11 (security — đọc lý thuyết thay vì implement đủ)

**Không bao giờ bỏ hẳn**: Phase 12 — project không kể được thành câu chuyện thì không có giá trị phỏng vấn.