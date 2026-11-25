# Cài đặt iOS

## Sự thật phải biết trước

**Không app bên thứ ba nào trên iOS đọc được notification của app khác**
— Kotlin hay Swift đều vậy. Trên iOS, đường capture duy nhất là
**Shortcuts automation (iOS 27+)**: khi có notification từ một app, iOS
chạy Shortcut của bạn, Shortcut đưa payload cho app NotifyPush, app
dispatch qua shared dispatcher (code chung với Android).

Hệ quả:

- Phải tạo **1 automation cho mỗi app nguồn** (Vietcombank, MB, MoMo...
  mỗi app một automation).
- iOS dưới 27 không có trigger notification → chỉ chạy Shortcut bằng tay
  (mở Shortcut → Run, hoặc Share → Run Shortcut).
- App phải được mở (ít nhất 1 lần sau khi nhận handoff) để dispatch đi —
  iOS không cho app chạy nền tự do như Android.

## Yêu cầu

- iPhone chạy **iOS 27 trở lên** (cho chế độ tự động).
- App NotifyPush đã cài (build bằng Xcode trên Mac — xem
  [README](../README.md#ios-chỉ-mac--xcode)).
- Shortcut NotifyPush đã import (`ios-shortcut/` trong repo).

## 1. Bật thông báo cho từng app nguồn — đủ 3 nơi

Automation chỉ kích hoạt khi thông báo **thực sự được hiển thị**. Bật
thiếu là nguyên nhân số 1 khiến automation "không chạy".

1. **Settings → Notifications → [tên app, vd Vietcombank]** → bật
   **Allow Notifications**.
2. Trong cùng màn hình đó, bật cả 3 vị trí: **Lock Screen**,
   **Notification Center** và **Banners**.
   - **Chỉ bật Banners thôi là không đủ**: thông báo phải vào được
     Notification Center thì trigger mới bắt được.
3. Mở app nguồn → cài đặt trong app → bật **thông báo biến động số dư**
   (hoặc loại thông báo bạn muốn forward). App nguồn không đẩy push thì
   không có gì để automation bắt.

## 2. Import Shortcut NotifyPush

1. Mở file Shortcut trong `ios-shortcut/` (hoặc link import do repo cung cấp).
2. Import vào app **Shortcuts**, kiểm tra các action đã đủ (lấy
   notification → đóng gói JSON `notify-push/v1` → đưa cho app NotifyPush).
3. Shortcut dùng chung 1 cái cho mọi app nguồn — không cần import nhiều lần.

## 3. Tạo Automation cho từng app nguồn (iOS 27+)

**Một automation cho mỗi app nguồn.** Không gộp chung, vì trigger gắn
với từng app.

1. Mở app **Shortcuts** → tab **Automation** (dưới cùng) → **+** →
   **Create Personal Automation**.
2. Chọn trigger **"When I receive a notification from [app]"** →
   chọn 1 app nguồn (vd Vietcombank).
3. Action: chạy Shortcut **NotifyPush** (đã import ở mục 2).
4. Chọn **Run Immediately** (chạy ngay, không hỏi trước).
5. **BẬT Automation lên** (toggle ở màn hình Automation). Đây là bẫy hay
   gặp nhất: automation tạo/import xong có thể ở trạng thái OFF.
6. Lặp lại cho mỗi app nguồn khác (MB, MoMo, Techcombank, ...).

## 4. URL-scheme handoff — Shortcut đưa payload cho app

Đường chính: Shortcut gọi URL scheme của app:

```
pushnotification://ingest?payload=<JSON notify-push/v1, URL-encoded>
```

App nhận handoff, parse payload, chạy qua `OtpSkipFilter` rồi dispatch
qua shared dispatcher như Android. Handoff cần app đã cài; nếu app chưa
cài, URL scheme không mở được gì.

Đường dự phòng: Shortcut POST trực tiếp tới destination (bỏ qua app).
Chỉ dùng khi app chưa kịp cài / handoff lỗi — khi đó payload đi thẳng
từ Shortcut tới server, không qua History / RetryQueue của app.
Cấu hình đường dự phòng nằm trong chính Shortcut (xem
`ios-shortcut/` — tên action và field chính xác do subagent iOS chốt;
chưa xác nhận trong repo này, xem [README](../README.md#chưa-được-kiểm-chứng)).

## 5. Test tay

1. **Test handoff**: mở Shortcut NotifyPush → **Run** tay (chạy trực
   tiếp trong app Shortcuts). App NotifyPush phải mở lên và History có
   thêm 1 entry. Nếu app không mở → xem
   [TROUBLESHOOTING](TROUBLESHOOTING.md#7-app-ios-không-nhận-handoff).
2. **Test automation**: thực hiện một giao dịch nhỏ để app nguồn đẩy
   notification thật → automation chạy → app nhận handoff → History có
   entry "đã gửi" → kiểm tra phía server nhận.
3. **Test từng service**: trong app NotifyPush → màn hình Services →
   chọn service → **Gửi test** (giống Android).

Đừng dùng giao dịch OTP để test — OTP bị bộ lọc chặn trước khi gửi.

## 6. Retry trên iOS

- Mất mạng / server `5xx` / timeout: payload vào hàng chờ retry của app.
- iOS không cho chạy nền tự do nên **không có WorkManager như Android**:
  retry chạy khi bạn **mở app** (app tự gửi lại hàng chờ lúc khởi động)
  hoặc bấm **Gửi lại** tay trong màn hình History.
- `401` / `422`: không retry (như Android).

## Giới hạn cứng trên iOS

- Không đọc được notification của app khác nếu không qua automation.
- 1 automation / 1 app nguồn — thêm app nguồn mới là phải tạo automation mới.
- Notification phải vào Lock Screen / Notification Center — chỉ có banner
  thì trigger không bắt.
- Automation có thể bị iOS tắt sau khi cập nhật iOS / restore máy —
  kiểm tra lại toggle ON sau mỗi lần cập nhật lớn.
- Không có retry nền tự động; hàng chờ chỉ gửi lại khi mở app hoặc retry tay.

## Checklist

- [ ] iOS 27+, thông báo app nguồn bật đủ Lock Screen + Notification Center + Banners
- [ ] Shortcut NotifyPush đã import
- [ ] Đã tạo 1 automation / app nguồn, chế độ Run Immediately
- [ ] Automation đang ở trạng thái **ON** (kiểm tra lại sau khi tạo!)
- [ ] Test tay: chạy Shortcut → app mở, History có entry
- [ ] 1 giao dịch thật đã tới được server nhận
