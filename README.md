# NotifyPush — Generic Notification Forwarder (Android, KMP v2.0.0)

NotifyPush nhận thông báo từ các app khác trên điện thoại Android và
chuyển tiếp nguyên văn tới các dịch vụ do bạn cấu hình (mail tool,
webhook, n8n, ...). Một payload JSON chung duy nhất — **không parse
trong client**.

> iOS đã bỏ theo quyết định 25/11 — có thể thêm lại sau.

> **Trạng thái:** v2.0.0 đã build và test xanh trên VM ngày 25/11/2026
> (xem [Chưa được kiểm chứng](#chưa-được-kiểm-chứng)).

## Mặc định an toàn

Mọi thứ nhạy cảm đều **tắt sẵn** cho tới khi bạn bật rõ ràng:

- **Service mới thêm vào mặc định TẮT** (`enabled: false`). Service
  chưa bật không nhận gì hết — kể cả khi bạn bấm "Gửi test" trên nó
  thì test vẫn đi qua dispatcher (chỉ để kiểm tra kết nối), nhưng
  notification thật thì không. Màn hình Services hiện rõ service nào
  chưa bật: dòng mờ đi + nhãn **"Chưa bật"**. Muốn nhận thật thì bật
  toggle của service đó.
- **Lọc mã OTP mặc định BẬT** — thông báo chứa mã OTP / mã xác thực
  không rời khỏi máy. Tắt trong màn hình **Cài đặt**, app cảnh báo rõ
  là tắt thì mã OTP cũng sẽ được gửi đi.
- **Lưu lịch sử mặc định BẬT**, giữ 30 ngày — tắt trong **Cài đặt** thì
  không lưu gì trên máy.
- **Tự gửi lại khi lỗi mặc định BẬT**, mỗi 15 phút — tắt trong
  **Cài đặt** thì các lần gửi lỗi vẫn nằm trong hàng chờ cho tới khi
  bạn bấm "Retry now" trên màn hình chính.

## App làm gì — và KHÔNG làm gì

**Làm:**

- Nhận thông báo từ app khác qua `NotificationListenerService`.
- Đóng gói thành một JSON chung duy nhất (schema `notify-push/v1`):

```json
{
  "sourceApp": "Vietcombank",
  "bundleId": "com.vietcombank.vcb",
  "title": "Vietcombank",
  "subtitle": "",
  "body": "TK 1234567890: +500.000 VND luc 10:23 25/11/2026. So du: 12.345.678 VND.",
  "receivedAt": "2026-11-25T10:23:00+07:00",
  "rawText": "Vietcombank\nTK 1234567890: +500.000 VND luc 10:23 25/11/2026. So du: 12.345.678 VND.",
  "device": "android"
}
```

| Field | Bắt buộc | Ghi chú |
|---|---|---|
| `sourceApp` | Có | Tên app hiển thị, vd `Vietcombank`. |
| `bundleId` | Có (có thể `""`) | Package name của app nguồn. |
| `title` / `subtitle` / `body` | Có (có thể `""`) | Nội dung notification, giữ nguyên. |
| `receivedAt` | Có | ISO-8601, thời điểm nhận trên máy. |
| `rawText` | Có | `title + "\n" + subtitle + "\n" + body`, bỏ phần trống. **Đây là nguồn sự thật — server parse lại từ field này.** |
| `device` | Có | String, `"android"` — thiết bị gửi payload. |

- Fan-out tới **tất cả** destination đang bật, chạy song song; một
  destination lỗi không chặn các destination khác.
- Hàng chờ retry riêng cho từng destination khi mất mạng / server lỗi
  (backoff lũy thừa; WorkManager chạy nền theo chuỗi one-time work,
  vì PeriodicWorkRequest tối thiểu 15 phút nên không dùng được cho
  chu kỳ 5 phút).
- Lọc bỏ thông báo chứa mã OTP / mã xác thực trước khi gửi
  (mặc định BẬT, cấu hình được trong màn hình **Cài đặt**).
  Xem [docs/PRIVACY.md](docs/PRIVACY.md).
- Lưu lịch sử đã gửi / đang chờ / lỗi trên máy để kiểm tra lại
  (mặc định BẬT, giữ 30 ngày, cấu hình được trong **Cài đặt**).

**KHÔNG làm (rõ ràng, theo thiết kế):**

- **Không parse nội dung trong client.** Không tách số tiền, số dư, tên
  ngân hàng, không nhận diện ngân hàng. Việc parse thuộc về từng server
  nhận (vd mail tool parse từ `rawText`).
- Không đọc tin nhắn SMS, email, hay nội dung trong app nguồn — chỉ đọc
  text của notification đã hiển thị.
- Không gửi gì đi nếu không có destination nào đang bật.
- Không đồng bộ, không backup, không phân tích dữ liệu lên bất cứ đâu
  ngoài các destination bạn tự cấu hình.

## Kiến trúc

```
                ┌────────────────────────────────────────────┐
                │  shared (KMP, :shared — android + jvm)    │
                │  NotificationPayload · Destination        │
                │  Dispatcher (fan-out song song)           │
                │  RetryQueue · OtpSkipFilter · AppSettings │
                │  TestPayloadBuilder · HistoryStore        │
                └──────────────────────┬─────────────────────┘
                                       │
        ┌──────────────────────────────┘
        │ Android (:app, Kotlin, Views XML)
        │ MainActivity · PushListenerService
        │ Services · History · Settings · RetryWorker
        └──────────────────────────────┘
   Capture: NotificationListenerService
   (đọc notification trực tiếp)
```

- Android app viết bằng Kotlin, giao diện Views XML — **không dùng Compose**.
- Module `:shared` giữ dạng KMP (android + jvm) để test đơn vị chạy
  được trên VM; target jvm chỉ phục vụ test, không phải app riêng.

## Cài đặt

- Android: [docs/SETUP-ANDROID.md](docs/SETUP-ANDROID.md)
- Thêm / sửa destination: [docs/DESTINATIONS.md](docs/DESTINATIONS.md)

## Cấu hình Services — 3 ví dụ

Mỗi destination gồm: `name`, `url`, `headerName` + `headerValue`,
`enabled`, `apps` (danh sách app nguồn được phép; rỗng = tất cả).
Chi tiết đầy đủ + cách thêm service mới trong 5 phút:
[docs/DESTINATIONS.md](docs/DESTINATIONS.md).

**Lưu ý:** các ví dụ dưới đây để `enabled: false` theo mặc định an
toàn — sau khi thêm service, bạn phải bật toggle của nó trong màn
hình Services thì notification thật mới được gửi tới.

**1. Mail tool (destination đầu tiên)** — server tự parse từ `rawText`:

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

API này do task khác xây riêng; repo này chỉ gửi payload tới đó, không
làm việc phía server. `2xx` / `422` đều tính là đã gửi xong (422 = server
đã nhận nhưng không parse được format — gửi lại cũng vậy, không retry).

**2. Generic webhook** — nhận raw JSON, tự xử lý phía server của bạn:

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

**3. n8n webhook** — nối tiếp vào workflow n8n:

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

`apps` ở ví dụ 3 chỉ nhận notification từ Vietcombank; 2 ví dụ trên
nhận từ mọi app (`apps` rỗng).

## Cài đặt trong app (màn hình Cài đặt)

Mở từ màn hình chính → **Cài đặt**. Tất cả lưu trên máy
(`settings.json`), mặc định như sau:

| Cài đặt | Mặc định | Lựa chọn |
|---|---|---|
| Lọc mã OTP | Bật | Tắt thì mã OTP cũng được gửi đi (app hiện cảnh báo) |
| Mẫu OTP tự thêm | (trống) | Mỗi dòng một mẫu, không phân biệt hoa/thường; mẫu sai cú pháp regex bị bỏ qua |
| Lưu lịch sử | Bật | Tắt thì không lưu gì trên máy |
| Giữ lịch sử | 30 ngày | 7 / 30 / 90 ngày — entry cũ hơn bị xóa khi đọc/ghi |
| Tự gửi lại khi lỗi | Bật | Tắt thì hàng chờ chỉ chạy khi bấm "Retry now" |
| Chu kỳ gửi lại | 15 phút | 5 / 15 / 60 phút |

## Luồng test

1. **Test notification cục bộ** (không cần app ngân hàng): mở app →
   màn hình chính → nhập tiêu đề/nội dung → bấm gửi test. App tự tạo
   notification rồi dispatch qua dispatcher như notification thật.
   (v1 có bug `Thread.sleep` trên UI thread trong vòng lặp gửi nhiều
   notification — v2 sửa bằng coroutines.)
2. **Test từng service**: màn hình Services → chọn service → "Gửi test".
   Kiểm tra phía server nhận được payload (log / lịch sử bên nhận).
   `401` = sai key, `422` = server nhận nhưng không parse được — cả hai
   đều không retry, xem [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
3. **End-to-end với notification ngân hàng thật**: bật capture theo
   [SETUP-ANDROID](docs/SETUP-ANDROID.md), thêm service rồi **bật
   toggle của nó**, thực hiện một giao dịch nhỏ → kiểm tra History
   trong app và phía server nhận. OTP / mã xác thực sẽ **không** tới
   server (bộ lọc mặc định) — đừng dùng giao dịch OTP để test end-to-end.

## Build

### Môi trường (VM này)

```sh
source ~/workspace/tools/android-env.sh
```

- Android SDK: `~/workspace/tools/android-sdk`
- JDK 17: `~/workspace/tools/jdk17`
- JVM bắt buộc: `-Djava.net.preferIPv4Stack=true`
- Java qua egress proxy cần import Hatch CA — xem `~/TOOLS.md`
  (mục "Java/Maven: import Hatch Egress CA vào cacerts").
- Chi tiết đầy đủ trong `~/TOOLS.md` (mục Go/Java/Maven/Android).

### Android (build được trên VM / Mac / Windows)

```sh
./gradlew :shared:jvmTest
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK ở `app/build/outputs/apk/debug/`.

## Quyền riêng tư

- API key chỉ lưu trên thiết bị, không commit vào repo, không gửi đi đâu
  ngoài chính destination đó. Xem [docs/PRIVACY.md](docs/PRIVACY.md).
- OTP / mã xác thực không được forward khi bộ lọc đang BẬT (mặc định).
- Không analytics, không bên thứ ba, không tài khoản.

## Xử lý sự cố

[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) — listener không chạy,
401/422, hàng chờ offline, gửi trùng.

## Chưa được kiểm chứng

Những điều **chưa xác nhận được** (không có môi trường để kiểm chứng):

- **Endpoint mail tool** `https://mail.hmt9x.dev/api/ingest/notification`
  do task khác xây — chưa verify live từ repo này.
- **Chạy thật trên điện thoại** — test trên VM chỉ chạy test đơn vị +
  build APK, chưa cài chạy trên máy thật (listener, WorkManager, màn
  hình Cài đặt/Services cần kiểm tay trên điện thoại).

Đã kiểm chứng ngày 25/11/2026 trên VM: `:shared:jvmTest` 49/49,
`:app:testDebugUnitTest` 34/34, `:app:assembleDebug` ra APK `app-debug.apk`
(versionName `2.0.0`). Tên class/màn hình đã đối chiếu với code:
`NotificationPayload`, `Dispatcher` (+ `createDispatcher`),
`PushListenerService`, `RetryWorker`, `ServicesActivity`, `HistoryActivity`,
`SettingsActivity`, `AppSettings` (+ `SettingsStore`).

### Stack v2 (đã có trong repo)

- Gradle `8.11.1`, AGP `8.5.2`, Kotlin `2.0.21`, Ktor `3.0.3`,
  kotlinx-serialization `1.7.3`, kotlinx-datetime `0.6.1`,
  kotlinx-coroutines `1.9.0`; `compileSdk`/`targetSdk` `35`, `minSdk` `24`,
  `versionName "2.0.0"`, package `com.god.pushnotification`.
- Module `:shared` (KMP: android + jvm), module `:app` (Android, Kotlin,
  Views XML), `docs/` (SETUP-ANDROID, DESTINATIONS, PRIVACY,
  TROUBLESHOOTING).
- `MainActivity` đã sang Kotlin, bug `Thread.sleep` trên UI thread đã sửa bằng
  coroutines (kèm nút Cancel + tiến độ); có `NotificationListenerService`,
  cấu hình destination, History, Cài đặt, retry (WorkManager chuỗi
  one-time work + retry tay).
