# Xử lý sự cố

## 1. Listener không chạy

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
- WorkManager gửi lại kể cả khi app đã đóng. Mở app → History
  sẽ hiện entry chuyển sang "đã gửi" sau khi retry thành công.
  Nếu đã tắt "Tự gửi lại khi lỗi" trong **Cài đặt** thì hàng chờ chỉ
  chạy khi bạn bấm **"Retry now"** trên màn hình chính.
- Nếu hàng chờ mãi không gửi được: kiểm tra mạng, kiểm tra URL
  destination còn đúng không, rồi bấm gửi lại tay 1 lần để xem mã lỗi
  mới nhất.

## 5. Gửi trùng (duplicate)

**Triệu chứng:** server nhận cùng một notification 2 lần trở lên.

Nguyên nhân thường gặp, theo thứ tự:

1. **Retry sau khi server đã nhận nhưng response bị mất**: app không
   nhận được `2xx` nên coi là chưa gửi và retry, trong khi server đã
   lưu. Phía server nên dedupe theo `(bundleId, receivedAt, rawText)`
   hoặc một idempotency key — đây là việc phía server, app không tự
   dedupe qua các lần chạy khác nhau.
2. **Hai destination trỏ cùng một endpoint**: kiểm tra lại màn hình
   Services, tắt cái trùng.

## 6. Test notification không hiện

- Kiểm tra quyền `POST_NOTIFICATIONS`: **Settings → Apps → NotifyPush
  → Notifications** → bật.
- Không có quyền này thì app vẫn nhận/forward notification app khác
  bình thường — chỉ không gửi được notification test cục bộ.
