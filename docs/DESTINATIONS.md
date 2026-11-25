# Destinations — Cấu hình nơi nhận notification

Một destination là một HTTP endpoint nhận payload JSON `notify-push/v1`.
App fan-out tới **tất cả** destination đang bật, chạy song song; một
destination lỗi không chặn các destination khác.

## Định dạng cấu hình

```json
{
  "destinations": [
    {
      "name": "mail-tool",
      "url": "https://mail.hmt9x.dev/api/ingest/notification",
      "headerName": "X-Api-Key",
      "headerValue": "DAN_KEY_CUA_BAN",
      "enabled": false,
      "apps": []
    }
  ]
}
```

> Service mới thêm vào **mặc định TẮT** (`enabled: false`) — phải bật
> toggle của nó trong màn hình Services thì notification thật mới được
> gửi tới. Màn hình Services hiện service chưa bật bằng dòng mờ +
> nhãn **"Chưa bật"**.

## Bảng field

| Field | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `name` | string | Có | Tên hiển thị duy nhất trong màn hình Services, vd `mail-tool`. Dùng để nhận diện destination trong History và log. |
| `url` | string | Có | URL đầy đủ, gồm scheme `https://`. App POST JSON tới đây. |
| `headerName` | string | Có (có thể `""`) | Tên header auth, vd `X-Api-Key`, `Authorization`. Để `""` nếu endpoint không cần auth. |
| `headerValue` | string | Có (có thể `""`) | Giá trị header, vd key API hoặc `Bearer <token>`. **Chỉ lưu trên thiết bị, không commit vào repo.** |
| `enabled` | boolean | Có | `false` = tắt hẳn, không gửi, không retry. **Mặc định `false` cho service mới thêm.** |
| `apps` | string[] | Có (có thể `[]`) | Lọc theo app nguồn (package name Android). Rỗng = nhận từ mọi app. |

### Bộ lọc OTP mặc định (bật sẵn)

```json
["otp", "mã otp", "ma otp", "verification code", "security code",
 "mã xác thực", "ma xac thuc", "mã xác nhận", "ma xac nhan",
 "mã bảo mật", "ma bao mat"]
```

Danh sách này khớp với `OtpSkipFilter` trong module shared. Bộ lọc kiểm tra trên
`rawText` (title + subtitle + body). Chỉ các từ khóa gắn với mã OTP mới bị chặn —
số 6 chữ số trần (vd mã tham chiếu giao dịch) KHÔNG bị chặn, để không làm mất
thông báo ngân hàng thật. Tắt/bật và thêm mẫu tự tạo trong màn hình **Cài đặt** — xem [docs/PRIVACY.md](PRIVACY.md).

## Ngữ nghĩa gửi / retry

| Kết quả | App làm gì |
|---|---|
| `2xx` | Đã gửi xong. |
| `422` | Đã gửi xong — server đã nhận nhưng không parse được format. **Không retry** (gửi lại cũng 422). |
| `401` / `403` | **Lỗi cấu hình**, không retry tự động. Kiểm tra lại key trong `headerValue`. |
| `404`, `5xx`, timeout, mất mạng | Chưa gửi xong → đưa vào hàng chờ retry riêng của destination đó (backoff lũy thừa; WorkManager chạy nền theo chuỗi one-time work, chu kỳ theo cài đặt tự gửi lại; hoặc bấm "Retry now" tay). |

## Thêm service mới trong 5 phút

1. Mở app → màn hình **Services** → **Thêm service**.
2. Điền `name` (đặt tên ngắn, vd `n8n`), `url`, `headerName` /
   `headerValue` (bỏ trống nếu endpoint public).
3. `apps`: để trống nếu muốn nhận mọi app, hoặc nhập package id
   của app nguồn (vd `com.vietcombank.vcb`).
4. Công tắc bật/tắt của service **mặc định TẮT** — bật lên thì mới
   nhận notification thật, rồi lưu.
5. Bấm **Gửi test** trên service vừa thêm → kiểm tra phía server đã
   nhận payload test chưa. Nếu nhận được: xong. Nếu lỗi: xem
   [docs/TROUBLESHOOTING.md](TROUBLESHOOTING.md) theo mã lỗi.

Không cần sửa code, không cần build lại app để thêm destination.

## Ví dụ 1 — Mail tool (destination đầu tiên)

Server mail tool tự parse số tiền / số dư / ngân hàng từ `rawText`.
API này do task khác xây riêng; repo này chỉ gửi tới.

```json
{
  "name": "mail-tool",
  "url": "https://mail.hmt9x.dev/api/ingest/notification",
  "headerName": "X-Api-Key",
  "headerValue": "DAN_KEY_CUA_BAN",
  "enabled": false,
  "apps": []
}
```

- Lấy key: tạo trong mail tool (hệ thống key `mt_...`, scope
  `ingest:write`) rồi dán vào `headerValue`. Key sai → `401`, không retry.
- `422` = mail tool nhận nhưng chưa có template parse cho format đó —
  payload đã được server lưu để review, không cần gửi lại.

## Ví dụ 2 — Generic webhook

Endpoint tự viết, nhận raw JSON `notify-push/v1` rồi xử lý tùy ý
(ghi log, lưu DB, bắn tiếp đi đâu...). Server của bạn phải tự parse
`rawText` — client không parse gì hết.

```json
{
  "name": "generic-webhook",
  "url": "https://example.com/hook",
  "headerName": "Authorization",
  "headerValue": "Bearer <redacted>",
  "enabled": false,
  "apps": []
}
```

Thay `https://example.com/hook` bằng URL thật, `Bearer <token>` bằng
token thật. Endpoint không cần auth thì để `headerName` và
`headerValue` là `""`.

## Ví dụ 3 — n8n webhook

Nối thẳng vào workflow n8n: tạo node Webhook (method POST, path
`/webhook/notify-push`), copy URL "Production URL" vào `url` bên dưới.
Ví dụ này chỉ nhận notification từ Vietcombank.

```json
{
  "name": "n8n",
  "url": "https://n8n.example.com/webhook/notify-push",
  "headerName": "",
  "headerValue": "",
  "enabled": false,
  "apps": ["com.vietcombank.vcb"]
}
```

- n8n webhook mặc định không có auth — nếu bật auth trong n8n thì điền
  `headerName`/`headerValue` tương ứng.
- Muốn nhận thêm app khác: thêm package id vào `apps`.
  Muốn nhận mọi app: để `apps` là `[]`.
- Trong n8n, payload vào ở dạng JSON — lấy text gốc từ `rawText`,
  đừng parse lại `title`/`body` rời rạc.

## Lưu ý bảo mật

- File cấu hình có chứa key — **không commit file có key thật vào repo**,
  không chụp màn hình có key gửi đi đâu.
- Key chỉ lưu trên thiết bị (xem [docs/PRIVACY.md](PRIVACY.md)).
- Dùng `https://` cho mọi destination. `http://` chỉ chấp nhận cho
  endpoint nội bộ / test local — không dùng cho dịch vụ thật.
