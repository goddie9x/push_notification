# Quyền riêng tư

## Cái gì rời khỏi thiết bị

Chỉ có **payload JSON `notify-push/v1`** rời khỏi thiết bị — và chỉ tới
các destination **bạn tự cấu hình** trong màn hình Services:

```json
{
  "sourceApp": "...",
  "bundleId": "...",
  "title": "...",
  "subtitle": "...",
  "body": "...",
  "receivedAt": "ISO-8601",
  "rawText": "title + subtitle + body",
  "device": "..."
}
```

Không có gì khác rời khỏi thiết bị:

- Không analytics, không crash report tự động, không quảng cáo.
- Không tài khoản, không đăng nhập, không đồng bộ cloud.
- Không gửi dữ liệu cho tác giả app hay bất kỳ bên thứ ba nào ngoài
  destination bạn cấu hình.
- Dữ liệu trên máy (cấu hình destination, History, hàng chờ retry) chỉ
  nằm trên máy — xóa app là mất.

## OTP / mã xác thực không bao giờ được forward

Bộ lọc `OtpSkipFilter` chạy **trước khi gửi**, trên `rawText`
(title + subtitle + body). Notification nào chứa một trong các mẫu sau
thì bị bỏ, không tới bất kỳ destination nào:

```
otp, mã otp, mã xác thực, mã xác nhận, mã bảo mật,
verification code, security code, mã code,
your code is, mã của bạn
```

- Bộ lọc **mặc định BẬT**. Tắt/bật và sửa danh sách trong màn hình Services.
- Tắt bộ lọc là tự chịu rủi ro: mã OTP sẽ đi thẳng tới server bạn cấu hình.
- Lưu ý giới hạn: bộ lọc chỉ bắt được mẫu text đã biết. Một mã xác thực
  viết theo cách lạ (không chứa các mẫu trên) có thể lọt qua — nếu
  destination của bạn nhạy cảm, lọc thêm phía server.

## Key chỉ ở trên thiết bị

- API key / token của destination (`headerValue`) chỉ lưu trong bộ nhớ
  của app trên thiết bị của bạn.
- Key **không** commit vào repo, **không** ghi vào doc, **không** gửi đi
  đâu ngoài chính destination đó (trong header của request tới URL bạn
  đã cấu hình).
- Mất máy = mất key trong app — thu hồi key phía server rồi tạo key mới.
- Không chụp màn hình có key gửi đi đâu; không dán key vào chat / issue
  công khai.

## App đọc được gì — và không đọc gì

- App chỉ đọc **text của notification đã hiển thị** từ các app nguồn
  (Android: qua `NotificationListenerService`; iOS: qua Shortcuts
  automation bạn tự tạo).
- App **không** đọc SMS, email, nội dung trong app nguồn, danh bạ, vị trí,
  ảnh, hay bất cứ dữ liệu nào khác.
- App **không parse** nội dung notification: không tách số tiền, số dư,
  tên ngân hàng. Việc parse (nếu có) do server nhận làm, theo cấu hình
  của chính bạn ở phía server.

## Thu hồi

- Tắt 1 destination: màn hình Services → tắt `enabled`. App dừng gửi
  tới đó ngay; hàng chờ của destination đó không gửi nữa.
- Dừng hẳn: tắt Notification access (Android) / tắt automation
  (iOS) → app không nhận thêm notification nào.
- Xóa dữ liệu: xóa app — cấu hình, key, History, hàng chờ đều mất theo.
