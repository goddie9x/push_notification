# Cài đặt Android

## Yêu cầu

- Android 8.0+ (minSdk 24 theo v1; số chính xác cho v2 do module `:app` chốt).
- App NotifyPush v2.0.0 đã cài (build từ [README](../README.md#build) hoặc file APK).

## 1. Cấp quyền Notification Listener (bắt buộc)

Đây là quyền để app đọc notification của app khác — không có quyền này
thì app không thấy gì hết.

1. Mở app NotifyPush → màn hình **Services** (hoặc màn hình chính) →
   bấm **Cấp quyền đọc thông báo** (app mở thẳng màn hình cài đặt).
   Hoặc làm tay: **Settings → Apps → Special app access →
   Notification access** (đường dẫn khác nhau tùy hãng máy, có thể là
   **Settings → Notifications → Device & app notifications →
   Notification access**).
2. Tìm **NotifyPush** trong danh sách → bật.
3. Quay lại app: trạng thái phải hiện **đã cấp quyền**. Nếu vẫn báo chưa
   cấp, tắt/bật lại một lần rồi kiểm tra lại (xem
   [TROUBLESHOOTING](TROUBLESHOOTING.md#1-listener-không-chạy-android)).

Lưu ý: một số máy (Xiaomi / Oppo / Vivo / Samsung) có thêm mục tối ưu
pin / chạy nền — nếu listener tự tắt sau một lúc, vào **Settings →
Battery** cho NotifyPush chạy nền không giới hạn (tên mục tùy hãng).

## 2. Cấp quyền POST_NOTIFICATIONS (cho test sender)

Android 13+ yêu cầu quyền này để app tự tạo notification test.

1. Mở app lần đầu, hệ thống hỏi quyền thông báo → bấm **Allow**.
2. Nếu đã bấm từ chối trước đó: **Settings → Apps → NotifyPush →
   Notifications** → bật.

Không có quyền này thì app vẫn **nhận và forward** notification của app
khác bình thường — chỉ không gửi được notification test cục bộ.

## 3. Thêm destination đầu tiên

1. Mở app → màn hình **Services** → **Thêm service**.
2. Điền theo [docs/DESTINATIONS.md](DESTINATIONS.md) — ví dụ nhanh nhất
   là mail tool:

   - `name`: `mail-tool`
   - `url`: `https://mail.hmt9x.dev/api/ingest/notification`
   - `headerName`: `X-Api-Key`
   - `headerValue`: key `mt_...` của bạn (scope `ingest:write`)
   - `enabled`: bật
   - `apps`: để trống (= nhận mọi app)
3. Lưu → bấm **Gửi test** trên service đó → kiểm tra phía server đã
   nhận payload chưa.

## 4. Luồng test

### Test notification cục bộ

1. Màn hình chính → nhập **tiêu đề** + **nội dung** test.
2. Chọn số lượng + khoảng cách (mặc định 1 notification).
3. Bấm gửi → notification test hiện lên, app dispatch qua dispatcher
   như notification thật → kiểm tra History + phía server.

### Test từng service

Màn hình **Services** → chọn service → **Gửi test**. Kết quả hiện ngay
(`2xx`/`422` = xong; `401` = sai key; lỗi mạng = vào hàng chờ).

### End-to-end với notification thật

1. Bật capture (mục 1) + thêm destination (mục 3).
2. Thực hiện một giao dịch nhỏ (vd chuyển khoản vài nghìn) để app ngân
   hàng đẩy notification biến động số dư.
3. Kiểm tra **History** trong app: phải có 1 entry "đã gửi" cho
   destination đó.
4. Kiểm tra phía server nhận (log mail tool / webhook / n8n).
5. Đừng dùng giao dịch OTP để test — OTP bị bộ lọc chặn trước khi gửi
   (theo thiết kế).

## 5. Retry — WorkManager + retry tay

- Mất mạng / server `5xx` / timeout: payload vào **hàng chờ retry
  riêng của từng destination**, gửi lại tự động với backoff lũy thừa
  (khoảng chờ tăng dần sau mỗi lần thất bại) qua WorkManager — kể cả khi
  app đã đóng.
- `401` / `422`: không retry tự động (xem bảng trong
  [DESTINATIONS](DESTINATIONS.md#ngữ-nghĩa-gửi--retry)).
- Retry tay: màn hình **History** → entry lỗi → **Gửi lại**. Chỉ gửi lại
  tới destination đã lỗi, không gửi lại toàn bộ.
- Hàng chờ lưu trên máy; xóa app = mất hàng chờ. Gửi xong thì entry
  chuyển sang "đã gửi" trong History.

## Checklist

- [ ] Notification access đã bật cho NotifyPush
- [ ] POST_NOTIFICATIONS đã cho phép (để test cục bộ)
- [ ] Ít nhất 1 destination đang `enabled`, test gửi thành công
- [ ] 1 giao dịch thật đã tới được server nhận
