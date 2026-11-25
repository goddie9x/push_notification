# NotifyPush — Generic Notification Forwarder (KMP v2.0.0)

NotifyPush nhận thông báo từ các app khác trên điện thoại và chuyển tiếp
nguyên văn tới các dịch vụ do bạn cấu hình (mail tool, webhook, n8n, ...).
Một payload JSON chung duy nhất — **không parse trong client**.

> **Trạng thái:** v2.0.0 đã build và test xanh trên VM ngày 25/11/2026
> (`:shared:jvmTest` 34/34, `:app:testDebugUnitTest` 31/31,
> `:app:assembleDebug` ra APK). Chỉ build iOS là chưa từng chạy (không có
> macOS/Xcode trên VM — xem [Chưa được kiểm chứng](#chưa-được-kiểm-chứng)).

## App làm gì — và KHÔNG làm gì

**Làm:**

- Nhận thông báo từ app khác (Android: `NotificationListenerService`;
  iOS: Shortcuts automation — xem bảng giới hạn bên dưới).
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
  "device": "<thiet-bi-gui>"
}
```

| Field | Bắt buộc | Ghi chú |
|---|---|---|
| `sourceApp` | Có | Tên app hiển thị, vd `Vietcombank`. |
| `bundleId` | Có (có thể `""`) | Bundle id / package name của app nguồn. Android: package name thật. iOS: Shortcut để `""` nếu không lấy được. |
| `title` / `subtitle` / `body` | Có (có thể `""`) | Nội dung notification, giữ nguyên. |
| `receivedAt` | Có | ISO-8601, thời điểm nhận trên máy. |
| `rawText` | Có | `title + "\n" + subtitle + "\n" + body`, bỏ phần trống. **Đây là nguồn sự thật — server parse lại từ field này.** |
| `device` | Có | String, `"android"` hoặc `"ios"` — thiết bị gửi payload. |

- Fan-out tới **tất cả** destination đang bật, chạy song song; một
  destination lỗi không chặn các destination khác.
- Hàng chờ retry riêng cho từng destination khi mất mạng / server lỗi
  (backoff lũy thừa; Android dùng WorkManager, iOS retry tay + khi mở app).
- Lọc bỏ thông báo chứa mã OTP / mã xác thực trước khi gửi
  (mặc định BẬT, cấu hình được). Xem [docs/PRIVACY.md](docs/PRIVACY.md).
- Lưu lịch sử đã gửi / đang chờ / lỗi trên máy để kiểm tra lại.

**KHÔNG làm (rõ ràng, theo thiết kế):**

- **Không parse nội dung trong client.** Không tách số tiền, số dư, tên
  ngân hàng, không nhận diện ngân hàng. Việc parse thuộc về từng server
  nhận (vd mail tool parse từ `rawText`). Repo Shortcut v1 cũ có parse
  phía client (`clientParsed`) — v2 **bỏ hẳn theo thiết kế**.
- Không đọc tin nhắn SMS, email, hay nội dung trong app nguồn — chỉ đọc
  text của notification đã hiển thị.
- Không gửi gì đi nếu không có destination nào đang bật.
- Không đồng bộ, không backup, không phân tích dữ liệu lên bất cứ đâu
  ngoài các destination bạn tự cấu hình.

## Kiến trúc

```
                ┌────────────────────────────────────────────┐
                │  shared (KMP, :shared)                    │
                │  NotificationPayload · Destination        │
                │  Dispatcher (fan-out song song)           │
                │  RetryQueue · OtpSkipFilter               │
                │  TestPayloadBuilder · HistoryStore        │
                └──────────────┬─────────────┬───────────────┘
                               │             │
        ┌──────────────────────┘             └──────────────────────┐
        │ Android (:app, Kotlin, Views XML)  │ iOS (iosApp/, SwiftUI)│
        │ MainActivity · PushListenerService │ 3 màn hình tương tự   │
        │ Services · History · RetryWorker   │ URL scheme / App Intent│
        └──────────────────────┘             └──────────────────────┘
   Capture: NotificationListenerService       Capture: iOS 27 Shortcuts
   (đọc notification trực tiếp)               automation → pushnotification://ingest
```

- Shared code giống hệt nhau trên 2 nền tảng; **chỉ đường capture khác nhau**.
- Android app viết bằng Kotlin, giao diện Views XML — **không dùng Compose**.
- iOS app dùng SwiftUI, gọi shared framework đã biên dịch.

## Giới hạn nền tảng — nói thật

| | Android | iOS |
|---|---|---|
| Đọc notification app khác | **Được**, qua `NotificationListenerService` (cần user cấp quyền trong Settings) | **Không được.** Không app bên thứ ba nào đọc được notification của app khác trên iOS — Kotlin hay Swift đều vậy. Đây là giới hạn của iOS, không phải của app. |
| Đường capture | Service chạy nền, tự động | iOS 27 Shortcuts automation "When I receive a notification from [app]" → đưa payload cho app NotifyPush qua App Intent / URL scheme `pushnotification://ingest` → app dispatch qua shared dispatcher |
| Cần làm tay | Cấp quyền 1 lần | **1 automation cho mỗi app nguồn**; iOS dưới 27 không có trigger này, chỉ chạy Shortcut tay |
| Retry nền | WorkManager | Retry tay + khi mở app (iOS không cho chạy nền tự do) |

## Cài đặt

- Android: [docs/SETUP-ANDROID.md](docs/SETUP-ANDROID.md)
- iOS: [docs/SETUP-IOS.md](docs/SETUP-IOS.md)
- Thêm / sửa destination: [docs/DESTINATIONS.md](docs/DESTINATIONS.md)

## Cấu hình Services — 3 ví dụ

Mỗi destination gồm: `name`, `url`, `headerName` + `headerValue`,
`enabled`, `apps` (danh sách app nguồn được phép; rỗng = tất cả),
`skipPatterns` (mẫu text bỏ qua). Chi tiết đầy đủ + cách thêm service
mới trong 5 phút: [docs/DESTINATIONS.md](docs/DESTINATIONS.md).

**1. Mail tool (destination đầu tiên)** — server tự parse từ `rawText`:

```json
{
  "name": "mail-tool",
  "url": "https://mail.hmt9x.dev/api/ingest/notification",
  "headerName": "X-Api-Key",
  "headerValue": "DAN_KEY_CUA_BAN",
  "enabled": true,
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
  "enabled": true,
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
  "enabled": true,
  "apps": ["com.vietcombank.vcb"]
}
```

`apps` ở ví dụ 3 chỉ nhận notification từ Vietcombank; 2 ví dụ trên
nhận từ mọi app (`apps` rỗng).

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
   [SETUP-ANDROID](docs/SETUP-ANDROID.md) / [SETUP-IOS](docs/SETUP-IOS.md),
   thực hiện một giao dịch nhỏ → kiểm tra History trong app và phía
   server nhận. OTP / mã xác thực sẽ **không** tới server (bộ lọc mặc
   định) — đừng dùng giao dịch OTP để test end-to-end.

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

### iOS (chỉ Mac + Xcode)

- Mở `iosApp/` bằng Xcode trên máy Mac, build shared framework rồi
  chạy app. **Không build được trên VM Linux này (không có macOS/Xcode)
  — đừng claim build iOS đã được verify ở đây.**
- Cần Xcode + iOS 27 (cho Shortcuts Notification automation) trên máy test.

## Quyền riêng tư

- API key chỉ lưu trên thiết bị, không commit vào repo, không gửi đi đâu
  ngoài chính destination đó. Xem [docs/PRIVACY.md](docs/PRIVACY.md).
- OTP / mã xác thực không bao giờ được forward (bộ lọc mặc định BẬT).
- Không analytics, không bên thứ ba, không tài khoản.

## Xử lý sự cố

[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) — listener không chạy,
401/422, hàng chờ offline, gửi trùng, automation iOS không chạy, app iOS
không nhận handoff.

## Chưa được kiểm chứng

Những điều **chưa xác nhận được** (không có môi trường để kiểm chứng):

- **Build iOS chưa từng được verify** — không có macOS/Xcode trên VM này.
  Lần build đầu tiên phải chạy trên Mac sau khi link framework
  `NotifyPushShared` (xem `iosApp/KMP-INTEGRATION.md`).
- **File `.shortcut` và Shortcut iOS chưa từng test trên iPhone thật** —
  plist đọc được, khớp `actions.json`, nhưng import/chạy thực tế cần bro test.
- **Endpoint mail tool** `https://mail.hmt9x.dev/api/ingest/notification`
  do task khác xây — chưa verify live từ repo này.

Đã kiểm chứng ngày 25/11/2026 trên VM: `:shared:jvmTest` 34/34,
`:app:testDebugUnitTest` 31/31, `:app:assembleDebug` ra APK `app-debug.apk`
(versionName `2.0.0`). Tên class/màn hình/framework đã đối chiếu với code:
`NotificationPayload`, `Dispatcher` (+ `createDispatcher`),
`PushListenerService`, `RetryWorker`, `ServicesActivity`, `HistoryActivity`,
framework iOS `NotifyPushShared`; màn hình iOS `ServicesView`, `TestView`,
`HistoryView` (+ `HistoryDetailView`).

### Stack v2 (đã có trong repo)

- Gradle `8.11.1`, AGP `8.5.2`, Kotlin `2.0.21`, Ktor `3.0.3`,
  kotlinx-serialization `1.7.3`, kotlinx-datetime `0.6.1`,
  kotlinx-coroutines `1.9.0`; `compileSdk`/`targetSdk` `35`, `minSdk` `24`,
  `versionName "2.0.0"`, package `com.god.pushnotification`.
- Module `:shared` (KMP: android + ios + jvm), module `:app` (Android, Kotlin,
  Views XML), `iosApp/` (Xcode, SwiftUI), `ios-shortcut/` (NotifyPush shortcut
  generic), `docs/` (SETUP-ANDROID, SETUP-IOS, DESTINATIONS, PRIVACY,
  TROUBLESHOOTING).
- `MainActivity` đã sang Kotlin, bug `Thread.sleep` trên UI thread đã sửa bằng
  coroutines (kèm nút Cancel + tiến độ); có `NotificationListenerService`,
  cấu hình destination, History, retry (WorkManager Android / thủ công +
  lúc mở app trên iOS).
