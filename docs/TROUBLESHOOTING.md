# Xử lý sự cố

## 1. Listener không chạy (Android)

**Triệu chứng:** có notification mới nhưng History trong app trống trơn.

1. Kiểm tra quyền: **Settings → Apps → Special app access →
   Notification access** → NotifyPush phải đang **bật**. Nếu đã bật mà
   vẫn không chạy: tắt rồi bật lại.
2. Kiểm tra app nguồn có thực sự đẩy notification không: kéo thanh
   thông báo xuống xem có notification đó không. Không có push thì
   không có gì để đọc.
3. Kiểm tra tối ưu pin: một số máy (Xiaomi / Oppo / Vivo / Samsung) tự
   tắt service chạy nền — vào **Settings → Battery** cho NotifyPush
   chạy nền không giới hạn (tên mục tùy hãng).
4. Khởi động lại máy rồi kiểm tra lại — service listener đôi khi cần
   reboot để đăng ký lại sau khi cập nhật app.
5. Kiểm tra bộ lọc: notification chứa từ khóa OTP (xem
   [PRIVACY](PRIVACY.md)) bị chặn **trước khi ghi History** — đó không
   phải lỗi, là thiết kế. Test bằng notification không chứa OTP.

## 2. Lỗi 401 / 403 — sai key

**Triệu chứng:** History báo `401` hoặc `403` cho một destination.

- 401/403 là **lỗi cấu hình, không retry tự động** (retry cũng 401).
- Kiểm tra `headerValue` trong màn hình Services: đúng key chưa, có
  thừa khoảng trắng / xuống dòng khi copy-paste không.
- Mail tool: key phải là key `mt_...` có scope `ingest:write`. Key của
  dịch vụ khác / key đã thu hồi đều 401.
- Sửa key xong → bấm **Gửi test** lại trên service đó. Đừng đợi retry
  tự động — không có.

## 3. Lỗi 422 — server nhận nhưng không parse được

**Triệu chứng:** History báo `422`.

- 422 **không phải lỗi của app** và **không retry**: server đã nhận
  payload và đã lưu `rawText` để review, chỉ là chưa có template parse
  cho format đó.
- Gửi lại cùng payload cũng sẽ 422 tiếp — đừng bấm gửi lại vô ích.
- Cách xử lý: báo cho người quản lý server nhận (vd mail tool) bổ sung
  template cho format mới; các bản ghi đã lưu sẽ được parse lại phía
  server.

## 4. Mất mạng / server 5xx — hàng chờ offline

**Triệu chứng:** History báo lỗi mạng / `5xx`, entry nằm trong hàng chờ.

- Đây là hành vi đúng: payload đã vào **hàng chờ retry riêng của
  destination** đó, sẽ gửi lại tự động với backoff lũy thừa.
- Android: WorkManager gửi lại kể cả khi app đã đóng. Mở app → History
  sẽ hiện entry chuyển sang "đã gửi" sau khi retry thành công.
- iOS: không có retry nền — mở app để app tự gửi lại hàng chờ, hoặc
  bấm **Gửi lại** tay trong History.
- Nếu hàng chờ mãi không gửi được: kiểm tra mạng, kiểm tra URL
  destination còn đúng không, rồi bấm gửi lại tay 1 lần để xem mã lỗi
  mới nhất.

## 5. Gửi trùng (duplicate)

**Triệu chứng:** server nhận cùng một notification 2 lần trở lên.

Nguyên nhân thường gặp, theo thứ tự:

1. **Automation iOS chạy trùng** (automation + chạy Shortcut tay cùng
   lúc) — chỉ chạy một đường.
2. **Retry sau khi server đã nhận nhưng response bị mất**: app không
   nhận được `2xx` nên coi là chưa gửi và retry, trong khi server đã
   lưu. Phía server nên dedupe theo `(bundleId, receivedAt, rawText)`
   hoặc một idempotency key — đây là việc phía server, app không tự
   dedupe qua các lần chạy khác nhau.
3. **Hai destination trỏ cùng một endpoint**: kiểm tra lại màn hình
   Services, tắt cái trùng.

## 6. Automation không chạy (iOS)

**Triệu chứng:** có notification ngân hàng nhưng Shortcut không chạy gì cả.

Kiểm tra theo thứ tự — đây là các nguyên nhân hay gặp nhất:

1. **Automation đang OFF?** Mở Shortcuts → tab **Automation** → mở
   automation của app đó → toggle phải ở **ON**. Automation tạo/import
   xong mặc định có thể OFF.
2. **Thông báo của app nguồn đã bật đủ chưa?** Settings →
   Notifications → [app] → bật Allow Notifications + cả 3 vị trí
   **Lock Screen, Notification Center, Banners**. Chỉ bật Banners thì
   trigger không bắt (thông báo phải vào được Notification Center).
3. **App nguồn có đẩy push không?** Mở app nguồn → cài đặt trong app →
   bật thông báo biến động số dư. Không có push thì không có gì để bắt.
4. **Đúng app chưa?** Mỗi automation gắn với 1 app nguồn — notification
   từ app khác không kích hoạt automation của app này.
5. **Vừa cập nhật iOS / restore máy?** Kiểm tra lại toggle ON — iOS có
   thể tắt automation sau cập nhật lớn.
6. iOS dưới 27: không có trigger notification — chỉ chạy Shortcut tay.

## 7. App iOS không nhận handoff

**Triệu chứng:** Shortcut chạy nhưng app NotifyPush không mở / History
không có entry mới.

1. Kiểm tra app đã cài chưa, mở app tay 1 lần cho chắc.
2. Kiểm tra URL scheme: Shortcut phải gọi
   `pushnotification://ingest?payload=<JSON đã URL-encode>`. Sai scheme
   hoặc payload chưa encode thì iOS không mở được app.
3. Kiểm tra payload JSON có hợp lệ không (chạy Shortcut trong app
   Shortcuts để xem lỗi từng action).
4. Đường dự phòng: nếu handoff lỗi mà cần gửi gấp, bật chế độ POST trực
   tiếp trong Shortcut (xem [SETUP-IOS](SETUP-IOS.md#4-url-scheme-handoff--shortcut-đưa-payload-cho-app))
   — payload đi thẳng tới server, không qua History / RetryQueue của app.
5. Ghi chú: tên action / field chính xác trong Shortcut do subagent iOS
   chốt; nếu tài liệu này lệch với Shortcut thực tế thì Shortcut là đúng.

## 8. Test notification không hiện (Android)

- Kiểm tra quyền `POST_NOTIFICATIONS`: **Settings → Apps → NotifyPush
  → Notifications** → bật.
- Không có quyền này thì app vẫn nhận/forward notification app khác
  bình thường — chỉ không gửi được notification test cục bộ.
