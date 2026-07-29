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
| 2 | SseEmitter API & vòng đời đầy đủ | 🟨 Đang học | |
| 3 | Emitter Registry (push đúng 1 user) | 🔲 Chưa bắt đầu | |
| 4 | Reliability (heartbeat, reconnect, Last-Event-ID, at-least-once) | 🔲 Chưa bắt đầu | Bắt buộc cho ngân hàng — mất event "liên kết thành công" là hỏng nghiệp vụ |
| 5 | Hạ tầng (nginx buffering, HTTP/1.1 connection limit, timeout ALB/Cloudflare) | 🔲 Chưa bắt đầu | |
| 6 | Multi-instance (Redis pub/sub hoặc Kafka fan-out) | 🔲 Chưa bắt đầu | Bài tập vàng: nối `PaymentEventProducer` (bankos) vào registry SSE |
| 7 | Security (EventSource không gửi được custom header, auth theo SecurityContext) | 🔲 Chưa bắt đầu | |
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
