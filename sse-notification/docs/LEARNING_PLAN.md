# SSE Mastery Plan — từ SseEmitter cơ bản đến production (transaction / link-bank notification)

> File này là nguồn sự thật duy nhất về tiến độ học. Sau mỗi buổi, cập nhật cột **Trạng thái** + mục **Nhật ký** ở cuối file. Khi mở session Claude mới, dán nguyên văn phần "Cách resume session mới" ở cuối file.

**Mục tiêu cuối:** thiết kế và implement được hệ thống push notification qua SSE cho use case giao dịch / liên kết ngân hàng (transaction, link-bank) — chịu được multi-instance, không mất event, bảo mật đúng user.

**Repo dùng để học:** `sse-notification` (demo/sandbox) + `../exercises/bankos` (nơi nối use case thật vào, đặc biệt `PaymentEventProducer`).

---

## Bảng tiến độ

| # | Chặng | Trạng thái | Ghi chú |
|---|-------|:---:|---|
| 0 | Wire protocol (`text/event-stream` raw) | 🔲 Chưa xong | Đã giao bài `curl -N`, chưa nhận output |
| 1 | Servlet async model (tại sao SseEmitter tồn tại) | ✅ Xong | Đã log đủ 3 thread: request ban đầu = `http-nio-8080-exec-1`, sending event = `pool-2-thread-1`, `onCompletion` = `http-nio-8080-exec-4`. Hiểu đúng: request thread trả về pool ngay khi return `SseEmitter`; callback lifecycle luôn chạy trên thread container (Tomcat) khác, không bao giờ là executor thread của app dù chính executor đó gọi `complete()`. |
| 2 | SseEmitter API & vòng đời đầy đủ | ✅ Xong | Bảng trạng thái→sự kiện→callback đầy đủ trong Nhật ký. Phát hiện quan trọng: Tomcat luôn tự phát hiện disconnect/timeout độc lập với thread producer; `onError` chưa từng thực chạy trong test (Tomcat luôn complete trước); `completeWithError()`/`send()` gọi trên emitter đã completed là no-op hoặc ném `IllegalStateException` tại chỗ, không qua callback nào. |
| 3 | Emitter Registry (push đúng 1 user) | ✅ Xong | `SseEmitterRegistry`/`Impl` + `NotificationController` (`/sse/subscribe/{userId}`, `POST /notify/{userId}`). Test thật: cách ly đúng userId, multi-tab cùng user đều nhận event. |
| 4 | Reliability (heartbeat, reconnect, Last-Event-ID, at-least-once) | ✅ Xong (phần lõi) | Bắt buộc cho ngân hàng — mất event "liên kết thành công" là hỏng nghiệp vụ. Đã test thật qua browser: reconnect tự động + replay đúng, không trùng lặp. Còn treo: buffer chưa bền vững qua restart (để dành Chặng 6). |
| 5 | Hạ tầng (nginx buffering, HTTP/1.1 connection limit, timeout ALB/Cloudflare) | ✅ Xong | Đã thêm header `X-Accel-Buffering: no` vào `/sse/subscribe`. Hiểu rõ HTTP/1.1 ~6 kết nối/domain vs HTTP/2 multiplexing (1 kết nối, nhiều stream). Idle timeout ALB (~60s)/Cloudflare (~100s) — heartbeat phải ngắn hơn giá trị chặt nhất trong chuỗi, cần xác nhận với team hạ tầng. |
| 6 | Multi-instance (Redis pub/sub hoặc Kafka fan-out) | 🔲 Chưa bắt đầu | Bài tập vàng: nối `PaymentEventProducer` (bankos) vào registry SSE |
| 7 | Security (EventSource không gửi được custom header, auth theo SecurityContext) | ✅ Xong (phần lõi) | Học sớm hơn thứ tự gốc — phát sinh từ câu hỏi thật về `bankos` (`request-id`/`x-signature` không gửi được qua `EventSource`). Đã implement JWT + Spring Security + `fetch()`-based client, test xác nhận 401 khi thiếu/sai token, `userId` chỉ lấy từ token, không còn IDOR. |
| 8 | Ops & phán đoán (metrics, graceful shutdown, SSE vs WebFlux vs WebSocket) | 🔲 Chưa bắt đầu | |

---

## Chi tiết từng chặng

### Chặng 0 — Nhìn thấy giao thức bằng mắt
- Học: format `event:`, `data:`, `id:`, `retry:`; 2 newline phân cách; `data:` nhiều dòng; dòng `:` là comment (heartbeat sau này).
- Bài tập: `curl -N -H "Accept: text/event-stream" http://localhost:8080/sse/numbers`, xem raw bytes.
- Checkpoint: giải thích vì sao `.name("number")` khiến `source.onmessage` trong `index.html` không chạy.

### Chặng 1 — Servlet async: nền móng
- Học: `AsyncContext` (Servlet 3.0+), request thread được trả về pool khi nào, `DeferredResult` vs `Callable` vs `SseEmitter`, timeout constructor vs `spring.mvc.async.request-timeout`.
- Bài tập: log tên thread ở 3 điểm (method controller / lambda executor / callback `onCompletion`).
- **Checkpoint đã đạt:** hiểu đúng lý do executor bắt buộc tồn tại (tránh giữ thread pool của Tomcat).
- **Còn treo:** xác nhận thread chạy `onCompletion` — đang debug vì log không xuất hiện (xem Nhật ký #1).

### Chặng 2 — API và vòng đời cho thật chắc
- Học: các overload `send()`, `SseEventBuilder`, `complete()` vs `completeWithError()`, ý nghĩa `onTimeout`, `IOException` khi client đóng tab.
- Bài tập nặng: đọc source `ResponseBodyEmitter`/`SseEmitter` đúng version đang dùng (Spring Framework 7.0.8, đi kèm Boot 4.1.0) — **không tin Stack Overflow/tutorial cũ**, version rất mới có thể khác hành vi kinh điển.
- Checkpoint: vẽ bảng trạng thái → sự kiện → trạng thái tiếp theo → callback nào được gọi.

### Chặng 3 — Registry: bước ngoặt thật sự
- Học: `ConcurrentHashMap<userId, emitter(s)>`, multi-tab (1 user nhiều emitter), dọn rác trong cả 3 callback, race condition giữa "đang send" và "vừa bị remove".
- Bài tập: `SseEmitterRegistry` với `subscribe(userId)`, `push(userId, event)`, endpoint `POST /notify/{userId}`; test cách ly bằng 2 tab 2 user.

### Chặng 4 — Reliability
- Học: heartbeat `: ping` mỗi 15–30s, auto-reconnect của browser, header `Last-Event-ID`, replay event bị lỡ, idempotency, at-least-once.
- Bài tập: rút mạng/kill server giữa chừng, quan sát reconnect; implement buffer chống mất event.

### Chặng 5 — Hạ tầng
- Học: nginx `proxy_buffering off`, `X-Accel-Buffering: no`, giới hạn ~6 kết nối/domain của HTTP/1.1, HTTP/2 multiplexing, idle timeout ALB/Cloudflare, gzip làm nghẽn stream.

### Chặng 6 — Multi-instance
- Vấn đề cốt lõi: emitter sống trong heap của 1 JVM; user giữ SSE ở pod A, event sinh ở pod B → pod B không có emitter để gọi.
- Học: fan-out Redis pub/sub, hoặc mọi instance cùng consume Kafka với consumer group riêng, sticky session (và vì sao đây là lựa chọn tệ).
- Bài tập vàng: nối `PaymentEventProducer` (đã có trong `../exercises/bankos`) vào registry SSE; chạy 2 instance khác port, chứng minh notification tới đúng user dù sinh ra ở instance khác.

### Chặng 7 — Security
- Học: `EventSource` không gửi được header `Authorization` tuỳ chỉnh → giải pháp cookie HttpOnly / token qua query param (rủi ro lộ log) / fetch-based SSE client; CORS with credentials; lấy `userId` từ `SecurityContext`, **không** từ path variable do client gửi.

### Chặng 8 — Ops & phán đoán
- Học: metrics số connection mở, graceful shutdown (đóng hết emitter khi SIGTERM), load test, so sánh `SseEmitter` (MVC) vs `Flux<ServerSentEvent>` (WebFlux) vs WebSocket — khi nào SSE là lựa chọn sai.

---

## Nhật ký (append theo thời gian, mới nhất lên trên)

### 2026-07-29 — Chặng 5: hạ tầng

Vì nginx/gateway do team khác quản lý, tập trung vào phần backend Java tự kiểm soát được:
- Thêm `response.setHeader("X-Accel-Buffering", "no")` trong `NotificationController.subscribe` — báo nginx tắt buffering cho riêng response này dù `proxy_buffering` global đang bật, không cần sửa `nginx.conf`.
- Hiểu cơ chế: HTTP/1.1 giới hạn ~6 kết nối TCP/domain (workaround của trình duyệt cho việc HTTP/1.1 mỗi kết nối chỉ phục vụ 1 request tại 1 thời điểm) — với SSE mở nhiều giờ, mỗi tab chiếm 1 chỗ, dồn ép các REST API khác của user vào hàng chờ. HTTP/2 giải bằng multiplexing (nhiều stream trên 1 kết nối TCP) — cần bật xuyên suốt cả chuỗi browser↔Cloudflare/ALB↔nginx↔app, không chỉ ở edge.
- Idle timeout: ALB mặc định ~60s, Cloudflare free ~100s (không chỉnh được) — heartbeat (Chặng 4, hiện 5s) phải luôn ngắn hơn giá trị chặt nhất trong toàn chuỗi; cần xác nhận con số thật với team hạ tầng, không đoán.

### 2026-07-29 — Chặng 7 (nhảy trước thứ tự): JWT auth cho SSE, xoá lỗ hổng IDOR

Xuất phát từ câu hỏi thật: "hiện tại ai cũng gọi vào `/sse/subscribe/{userId}` được, phải làm sao?" — đúng lỗ hổng IDOR (userId do client tự khai qua path).

Phân tích 2 hướng giải quyết:
1. **Ticket ngắn hạn** (đã viết thành doc riêng [`docs/SSE_AUTH_TICKET.md`](SSE_AUTH_TICKET.md), KHÔNG implement) — do `EventSource` không set được header (`Authorization`, và quan trọng hơn với `bankos`: `request-id`, `x-signature`), phải tách làm 2 bước: `POST /sse/ticket` (REST bình thường, verify đủ header) → trả về ticket ngắn hạn → `EventSource` dùng ticket qua query param. Bàn kỹ đánh đổi: ticket phải bỏ tính "dùng 1 lần" (giữ "dùng nhiều lần" vì `EventSource` reconnect tự động dùng lại y nguyên URL/ticket), đổi lại TTL = access token + revoke được khi logout. Ticket là scope hẹp (chỉ mở được đúng 1 kênh SSE của đúng 1 user) nên chấp nhận được việc bỏ "dùng 1 lần".
2. **Header trực tiếp qua `fetch()`** (ĐÃ IMPLEMENT) — nhận định chặt hơn ticket vì verify được đủ `request-id`/`x-signature` ngay tại chính request subscribe, không cần thêm store/vòng đời ticket riêng.

Implement thật hướng 2:
- `build.gradle`: thêm `spring-boot-starter-security` + `jjwt` (0.12.6).
- `security/JwtService.java`, `security/JwtAuthFilter.java` (OncePerRequestFilter đọc `Authorization: Bearer`, set `SecurityContext`), `security/SecurityConfig.java` (chỉ khoá `/sse/subscribe`, 401 qua `HttpStatusEntryPoint`, stateless, csrf tắt).
- `controller/AuthController.java`: `POST /auth/demo-login/{userId}` — **demo only**, đứng thay login thật.
- `NotificationController.subscribe`: đổi path `/sse/subscribe/{userId}` → `/sse/subscribe`, lấy `userId` từ `Authentication` (Spring MVC tự resolve tham số kiểu `Authentication`/`Principal` từ `request.getUserPrincipal()`) — **không còn field nào để client tự khai `userId`**, xoá lỗ hổng IDOR tận gốc kiến trúc, không phải chỉ validate.
- `index.html`: bỏ hẳn `EventSource`, viết tay `fetch()` + `ReadableStream` + parse SSE thủ công (`id:`/`event:`/`data:`, bỏ qua dòng comment) + vòng lặp reconnect tự set lại header mỗi lần — đúng kỹ thuật thư viện `@microsoft/fetch-event-source` làm bên trong.

Test xác nhận: thiếu token → 401; token bị tamper (sai chữ ký) → 401; token hợp lệ → nhận đúng notification qua header-based flow.

**Còn treo:** token/session hết hạn giữa lúc connection đang mở nhiều giờ không tự ngắt (chỉ validate lúc connect) — hướng khắc phục khả thi là tận dụng lại vòng heartbeat (Chặng 4) để re-check định kỳ, chưa implement. `/notify/{userId}` và `/debug/disconnect/{userId}` vẫn `permitAll` — đại diện cho lời gọi nội bộ giữa service (cần cơ chế auth khác, service-to-service, không phải scope buổi này).

### 2026-07-29 — Chặng 4 xác nhận thật qua browser: reconnect tự động + replay không trùng lặp

Cập nhật `index.html` trỏ vào `/sse/subscribe/user1` (log `onopen`/`onerror`/`notification` kèm `lastEventId` và giờ). Thêm 1 endpoint debug tạm thời `POST /debug/disconnect/{userId}` (gọi `registry.forceDisconnect()` → `emitter.complete()` cho toàn bộ emitter của user) để giả lập server chủ động đóng stream **mà không cần restart cả JVM** (tránh mất buffer — điều đã biết trước).

Kịch bản test (dùng Claude Browser pane + `curl`):
1. Mở `index.html`, `EventSource` connect `/sse/subscribe/user1` → `onopen`.
2. Push event id=1 ("event before disconnect") → nhận live ngay.
3. Gọi `/debug/disconnect/user1` → server `complete()` stream.
4. Ngay khi server đang "gián đoạn", push tiếp event id=2 ("event during gap").
5. Quan sát: `[onerror] readyState=0` lúc `11:30:08`, rồi ~10s sau **tự động** `[onopen] readyState=1` lúc `11:30:18` (không ai bấm gì, không reload trang) kèm ngay `[notification] id=2 ...` — đúng event bị lỡ, được replay nhờ `Last-Event-ID` browser tự gửi ngầm khi reconnect. Không có event nào bị lặp lại (id=1 không xuất hiện lần 2).

Xác nhận đúng điều ghi trong nhật ký ngày đầu tiên: `EventSource` tự động reconnect kể cả khi server chủ động gọi `emitter.complete()` (không phải lỗi mạng), không cần JS gọi `source.close()`. Đây là nguồn gốc bug "duplicate notification" nếu không có `Last-Event-ID` + buffer — nay đã có cơ chế đúng để tránh.

Chặng 4 coi như đạt phần lõi. Còn treo (đã ghi ở mục trước, không lặp lại): buffer không sống sót qua restart JVM thật (khác với "network blip" vừa test) — cần DB/Kafka để giải quyết, dành cho Chặng 6.

### 2026-07-29 — Chặng 4 (một phần): heartbeat + Last-Event-ID/replay buffer

Thêm vào `SseEmitterRegistryImpl`:
- **Heartbeat**: `ScheduledExecutorService` dùng chung (1 cho toàn bộ registry, không phải mỗi emitter 1 thread — quyết định đúng của người học vì lý do chi phí khi có hàng nghìn user), `@PostConstruct` lên lịch mỗi 5s gửi `SseEmitter.event().comment("ping")` cho toàn bộ emitter đang có, `@PreDestroy` shutdown scheduler. Test bằng `curl -N` xác nhận thấy `:ping` lặp lại đều đặn — đúng wire format comment học ở Chặng 0.
- **Last-Event-ID + replay buffer**: mỗi user có 1 `AtomicLong` sinh id tăng dần + 1 `Deque<BufferedEvent>` (bounded, giữ tối đa 50 event gần nhất). `push()` gán id cho event, lưu vào buffer trước khi gửi live. `subscribe()` nhận thêm `Long lastEventId` (đọc từ header `Last-Event-ID` ở controller), nếu có thì replay các event có `id > lastEventId` từ buffer trước khi tiếp tục nhận live event.
- Quyết định thiết kế quan trọng: buffer/idGenerator là 2 map **tách riêng** khỏi map `emitters`, và **không bị dọn dẹp** khi list emitter của user rỗng — ngược lại với map `emitters` (phải dọn để tránh leak). Lý do: buffer cần sống sót qua đúng khoảng thời gian user mất kết nối, nếu dọn theo cùng nhịp với emitters thì mất sạch dữ liệu ngay lúc cần replay nhất.
- Test thật (dùng Postman/`curl` giả lập, gửi tay header `Last-Event-ID`): subscribe → nhận event A, B → ngắt kết nối → push event C, D lúc "mất kết nối" → subscribe lại với `Last-Event-ID: 2` → nhận đúng lại C, D (không lặp A, B). Xác nhận cơ chế đúng.

**Còn treo:**
- Buffer vẫn chỉ sống trong RAM của 1 JVM — chỉ chịu được mất mạng tạm thời, KHÔNG chịu được restart server (đã phân tích kỹ ở phần đầu Chặng 4). Muốn thật sự bền vững phải đẩy qua DB/Redis/Kafka — để dành cho bài tập vàng Chặng 6 với `bankos`.
- Chưa test bằng browser thật (`EventSource` tự động gửi `Last-Event-ID` khi tự reconnect, có cơ chế `retry:` riêng) — mới giả lập tay bằng Postman/curl.
- Idempotency phía client (dedup theo id khi nhận trùng) chưa bàn tới.

### 2026-07-29 — Chặng 3 hoàn tất: SseEmitterRegistry

Xây `src/main/java/dev/hieunv/ssenotification/service/SseEmitterRegistry(Impl).java` + `controller/NotificationController.java`.

Thiết kế:
- `Map<String, CopyOnWriteArrayList<SseEmitter>>` — outer map là `ConcurrentHashMap` (tự sửa từ `HashMap` sau khi được hỏi lại — nhận ra lý do: `computeIfAbsent` đồng thời trên `HashMap` thường sẽ corrupt structure), value là `CopyOnWriteArrayList` (đọc nhiều hơn ghi, an toàn khi vừa iterate vừa bị sửa).
- `subscribe()`: tạo 1 `Runnable cleanup` dùng chung, đăng ký vào cả `onCompletion`/`onTimeout`/`onError` (bọc lambda cho `onError` vì nó nhận `Consumer<Throwable>` chứ không phải `Runnable`). Cleanup dùng `emitters.remove(name, userEmitters)` (2-arg, so sánh reference) thay vì `remove(name)` trơn — tránh race: list rỗng bị xóa nhầm ngay sau khi 1 tab mới của cùng user vừa tạo list mới.
- `push()`: lấy list theo `userId`, lặp gửi, mỗi emitter có `try/catch` riêng (1 emitter lỗi không chặn các emitter còn lại — quyết định đúng của người học, khớp yêu cầu nghiệp vụ ngân hàng). Lỗi thì gọi `emitter.completeWithError(e)` — không tự remove tay, tận dụng lại cleanup đã đăng ký ở `subscribe()`.
- Endpoint: `GET /sse/subscribe/{userId}` (timeout `Long.MAX_VALUE` — kết nối sống dài hạn, khác hẳn 2 demo stream ngắn hạn), `POST /notify/{userId}` (body là `String` message, đơn giản để test).

Test thật đã làm: 2 user khác nhau subscribe → push 1 user → chỉ đúng user đó nhận (cách ly đúng). 2 tab cùng 1 user subscribe → push → cả 2 tab đều nhận (multi-emitter đúng).

**Còn treo (chưa test thật, không chặn Chặng 4):** race condition thật giữa "đang send" và "vừa bị remove" dưới tải đồng thời cao — hiện tại chỉ mới thiết kế đúng về lý thuyết (2-arg `remove`, `CopyOnWriteArrayList`), chưa có bài test cụ thể để chứng minh bằng thực nghiệm.

### 2026-07-29 — Chặng 2 hoàn tất: bảng trạng thái → sự kiện → callback

Thực nghiệm 2 kịch bản thật (`curl -N` + Ctrl+C giữa chừng cho `/sse/numbers`; để `/sse/clock` tự chạy hết timeout 3s trong khi loop gửi 5 event mỗi 2s) và đối chiếu với luồng thành công đã log trước đó.

| # | Trạng thái trước | Sự kiện | Ai gây ra | Callback (đúng thứ tự) | Trạng thái sau |
|---|---|---|---|---|---|
| 1 | Active | Gửi hết event, code tự gọi `emitter.complete()` | App code | `onCompletion` — chạy trên thread Tomcat khác thread gọi `complete()` | Completed |
| 2 | Active | Client ngắt kết nối lúc executor đang idle/sleep | Tomcat tự phát hiện qua socket | `onCompletion` trực tiếp — **không có `onError`** | Completed |
| 3 | Active | Hết deadline timeout (cố định từ lúc tạo emitter, `send()` thành công KHÔNG reset) | Tomcat tự phát hiện | `onTimeout` → Spring WARN `AsyncRequestTimeoutException` (ignore, response đã committed) → `onCompletion` | Completed |
| 4 | Completed (từ #2/#3) | Executor thread không biết, vẫn gọi `emitter.send()` | App code | `send()` ném `IllegalStateException: ResponseBodyEmitter has already completed` ngay tại chỗ, không qua callback | Completed (không đổi) |
| 5 | Completed (từ #2/#3) | App bắt exception ở #4, gọi `completeWithError(e)` | App code | Không callback nào chạy — no-op vì đã completed | Completed (không đổi) |

**Kết luận quan trọng nhất:** trong mọi kịch bản test, Tomcat luôn tự phát hiện và complete emitter **độc lập với thread producer** — trước khi code app kịp phản ứng. Vì vậy `onCompletion` là điểm dọn dẹp duy nhất đáng tin cậy (luôn chạy, mọi nguyên nhân); còn `onError` chưa từng thực sự kích hoạt trong test hôm nay vì Tomcat luôn "về đích trước". Việc gọi `send()`/`completeWithError()` trên emitter đã completed là vô hại về mặt luồng chạy (không crash app) nhưng cần try/catch bao quanh vì `send()` ném exception thật.

**Còn treo (không chặn Chặng 3):** chưa tạo được kịch bản khiến `onError` thực sự chạy (ví dụ `IOException` khi đang ghi dở giữa lúc client vừa đóng — tức lỗi xảy ra nhanh hơn Tomcat kịp tự phát hiện). Có thể quay lại khi cần.

Cũng sửa 1 bug nhỏ trong lúc debug: `registerLifecycleLogs` bị thiếu tham số thread trong `onCompletion`/`onTimeout` (dòng log ghi "on thread" nhưng không có `{}` + giá trị) — đã bổ sung.

### 2026-07-29 — Chặng 1 hoàn tất: xác nhận thread onCompletion
Test lại bằng `curl -N`, đợi đủ ~5s. Log thu được:
```
streamNumbers (request)  -> http-nio-8080-exec-1
sending event (executor) -> pool-2-thread-1
onCompletion callback    -> http-nio-8080-exec-4
```
Xác nhận: `onCompletion` chạy trên thread container Tomcat khác cả 2 thread trên, dù chính `pool-2-thread-1` là bên gọi `emitter.complete()`. Lý do: `AsyncContext.complete()` gọi từ thread ngoài container buộc Tomcat phải tự dispatch việc gọi `AsyncListener.onComplete()` sang 1 worker thread riêng của nó — `exec-1` đã được trả về pool ngay lúc controller return `SseEmitter` nên không thể "quay lại" chạy callback, còn `pool-2-thread-1` không thuộc connector pool nên Tomcat không dùng được.

Cũng giải thích được vì sao thấy 2 dòng `"[numbers] completed"` giống hệt nhau trong log: đó là do 2 callback `onCompletion` khác nhau (1 từ `registerLifecycleLogs`, 1 đăng ký thủ công) — không phải do chain ghi đè hay bug, cả 2 log cùng chữ vì code viết trùng string.

Chuyển sang Chặng 2.

### 2026-07-29 — Bug đang debug: onCompletion không log
Code hiện tại (`SseController.streamNumbers`) đăng ký `onCompletion` **hai lần**: một lần trong `registerLifecycleLogs()` (log `"[numbers] completed"`), một lần thủ công thêm (log kèm tên thread). Giả thuyết ban đầu "callback thứ 2 ghi đè callback thứ 1" đã bị loại bỏ bằng cách **decompile trực tiếp bytecode** của `spring-webmvc-7.0.8.jar` (đúng version project dùng):

```
public void onCompletion(Runnable callback):
    completionCallback.addDelegate(callback)   // KHÔNG PHẢI completionCallback = callback
```

→ Spring dùng `DefaultCallback` nội bộ gom nhiều delegate (chain), không ghi đè. Cả hai log phải cùng chạy khi completion xảy ra thật sự.

Đang chờ xác nhận từ 3 câu hỏi debug:
1. Gọi endpoint bằng gì — tab `index.html`, URL trực tiếp trên browser, hay `curl`?
2. Có đợi đủ ~5s (10 event × 500ms sleep) trước khi xem log không?
3. Sau khi bắn xong 10 event, kết nối còn mở hay đã đóng?

Gợi ý quan trọng liên quan trực tiếp tới use case ngân hàng: `EventSource` trong browser **mặc định tự động reconnect** khi server đóng stream (`emitter.complete()`), trừ khi JS gọi `source.close()` chủ động. Đây là nguồn gốc rất nhiều bug "duplicate notification" trong thực tế — sẽ quay lại kỹ ở Chặng 4.

### 2026-07-29 — Baseline
- Đọc code có sẵn: `SseController` với `/sse/numbers` và `/sse/clock`, dùng `ExecutorService` riêng, có `onCompletion/onTimeout/onError`. Client `index.html` dùng `EventSource`, hiện đang trỏ vào `/sse/numbers` dù tiêu đề ghi "Clock stream" (mismatch nhỏ, chưa sửa).
- Stack: Spring Boot 4.1.0 / Spring Framework 7.0.8, Java 25 (toolchain), `spring-boot-starter-webmvc` (Servlet MVC, không phải WebFlux).
- Người học đã có nền tảng Spring Boot vững (đang làm song song dự án `bankos` — ví, giao dịch, RBAC), mới với async/SSE cụ thể.

---

## Cách resume session mới

Khi mở session Claude Code mới trong thư mục `sse-notification`, dán nguyên văn:

> Đọc file `docs/LEARNING_PLAN.md` trong project này để lấy lại context — tôi đang học SSE/SseEmitter theo lộ trình 9 chặng để áp dụng cho transaction/link-bank notification. Xem bảng tiến độ + nhật ký gần nhất để biết tôi đang ở đâu, rồi tiếp tục dạy tôi từ đó, giữ đúng phong cách hỏi-đáp từng bước một (không giảng một lèo).
