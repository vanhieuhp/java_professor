# Xác thực SSE bằng Ticket — thiết kế tham khảo

> Ghi chú thiết kế (chưa implement trong code). Sinh ra từ buổi học Chặng 7 (Security) khi thảo luận vấn đề thật của `bankos`: header `Authorization` + `request-id` + `x-signature` không gửi được qua `EventSource`.

## Vấn đề gốc

`EventSource` (chuẩn browser) chỉ nhận `(url, { withCredentials })` — **không có API để set header tuỳ ý**. Đây là giới hạn của spec, không phải của trình duyệt cụ thể nào.

Hệ quả: mọi thứ `bankos` đang yêu cầu trên REST API bình thường (`Authorization: Bearer <token>`, `request-id` chống replay, `x-signature` ký số) đều **không gửi được** nếu dùng `EventSource` thuần để mở kết nối SSE.

Nếu để `userId` đi qua path variable (`/sse/subscribe/{userId}`) như bản demo hiện tại — bất kỳ ai cũng subscribe được kênh của bất kỳ user nào (lỗi IDOR — Insecure Direct Object Reference).

## Cơ chế: ticket ngắn hạn, dùng cho đúng 1 việc

Tách làm 2 bước, thay vì cố nhét toàn bộ auth vào 1 request `EventSource`:

```
Bước 1 — REST bình thường (giữ nguyên hợp đồng có sẵn với gateway):
   POST /sse/ticket
   Headers: Authorization, request-id, x-signature   <- verify đầy đủ như mọi API khác
   ← { "ticket": "a1b2c3...", "expiresAt": "..." }

Bước 2 — mở SSE, không cần header:
   new EventSource('/sse/subscribe?ticket=a1b2c3...')
   → EventSource dùng được bình thường, giữ nguyên auto-reconnect + Last-Event-ID

Bước 3 — server đổi ticket → userId:
   server tra ticket trong store (Redis/DB), lấy ra userId đã bind sẵn từ bước 1
   KHÔNG lấy userId từ path/query do client tự khai
```

## Vì sao ticket phải là "reference token", không phải JWT tự chứa thông tin

Nếu ticket là JWT tự-verify (ký sẵn, không cần tra store), server **không thể vô hiệu hoá sớm** được nữa — JWT hợp lệ tới khi hết hạn, không có cách "xoá" giữa chừng khi user logout.

→ Ticket phải là 1 chuỗi random, tra vào store dạng `ticket -> {userId, expiresAt}`. Logout thì xoá record — ticket chết ngay, không đợi hết TTL.

## Đánh đổi: TTL ngắn vs dùng-một-lần

Ban đầu tưởng ticket nên "TTL ngắn (giây) + dùng 1 lần rồi huỷ" giống CSRF token. Nhưng việc này xung khắc với chính hành vi của `EventSource`:

> `EventSource` tự động reconnect khi mất kết nối, và nó gọi lại **y nguyên URL cũ** — tức dùng lại đúng ticket cũ. Nếu ticket bị đốt sau lần dùng đầu tiên, hoặc TTL chỉ vài giây, thì lần reconnect tự động đầu tiên đã fail.

Giải quyết bằng cách tách 2 thuộc tính bảo mật ra, giữ 1 bỏ 1 — có chủ đích:

| Thuộc tính | Giữ hay bỏ | Lý do |
|---|---|---|
| Dùng nhiều lần (không đốt sau 1 lần) | **Giữ khả năng dùng lại** | Bắt buộc, vì `EventSource` reconnect nhiều lần trong suốt phiên (có thể hàng giờ). Chấp nhận được vì ticket **scope rất hẹp** — chỉ mở được đúng 1 việc (subscribe SSE của đúng 1 userId), không gọi được API nhạy cảm khác (chuyển tiền, đổi mật khẩu). Lộ ticket = lộ khả năng nghe thông báo, không phải chiếm tài khoản. |
| Sống ngắn (giây) | **Bỏ, thay bằng TTL = access token** | Ticket không sống lâu hơn access token nó được sinh ra từ đó — rủi ro lộ ticket tương đương rủi ro lộ chính access token, hệ thống vốn đã chấp nhận sẵn mức rủi ro đó. |
| Revoke được | **Giữ** | Xử lý case access token bị thu hồi giữa chừng (logout, đổi mật khẩu, phát hiện gian lận) — xoá record trong store, ticket chết ngay lập tức. |

## Giới hạn còn lại (chưa giải quyết, ghi nhận để không quên)

- **Signature chỉ bảo vệ được request bước 1 (issue ticket)**, không bảo vệ được từng event đẩy về qua SSE sau đó (event về dạng plaintext qua kênh, không ký) — đây là quyết định thiết kế đã thống nhất trong buổi học: SSE là kênh **báo tin**, không phải nguồn sự thật. Client nhận event xong phải tự gọi lại REST API có ký để lấy dữ liệu thật (số dư, chi tiết giao dịch) trước khi hiển thị final cho user, không tin thẳng nội dung event.
- **Access token/ticket hết hạn giữa lúc connection đang mở nhiều giờ không tự ngắt kết nối** — vì việc validate chỉ xảy ra tại thời điểm connect (issue ticket + đổi ticket), không có gì chủ động kiểm tra lại trong suốt vòng đời emitter đang mở. Hướng khắc phục khả thi: tận dụng lại vòng heartbeat (`ScheduledExecutorService` đã có ở Chặng 4) để kiểm tra định kỳ ticket/session còn hợp lệ không, nếu không thì chủ động `completeWithError()` — nhưng chưa implement.
