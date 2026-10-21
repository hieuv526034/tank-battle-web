# 31. Màu nút, trạng thái đang xử lý và disabled

Cập nhật 21/10/2026. Đọc cùng [theme](26-doc-ui-theme-va-mobile.md) và
[bố cục bàn chơi](29-sua-loi-va-bo-cuc-ban-choi.md).

## Nguyên nhân và cách sửa

CSS cũ áp dụng opacity .45 cho mọi button:disabled. Event delegation cũng đặt disabled
trong lúc chờ Promise, nên nút tạm khóa khi gọi API/mở xác nhận bị làm mờ cả chữ, icon và nền.
Đã xác nhận computed style trên Chromium trước sửa: cả nút disabled nghiệp vụ và nút chờ đều .45.
Không có bằng chứng nút nghiệp vụ bị khóa sai điều kiện; không bỏ các điều kiện disabled để né màu.

- Nút bị khóa vì chưa đủ điều kiện: opacity 1, nền/chữ/viền xám riêng cho mỗi theme, cursor not-allowed.
- Nút đang xử lý: disabled để chặn lặp, aria-busy=true, giữ nhãn/màu gốc, vòng chờ theo currentColor
  và cursor progress. Không dùng màu disabled nghiệp vụ cho trạng thái này.
- Hover chỉ áp dụng nút còn dùng được. Active chỉ dịch vị trí, không làm tối chữ/nền.
- Hero/chiến trường/dialog giữ bảng màu tối riêng, kể cả khi theme toàn trang là sáng; baseline và
  hover nút primary/secondary có selector chung ở cuối CSS để tránh quy tắc nền sáng thắng nhầm.

## Luồng code

1. ui.js → setButtonBusy(element) ghi nhớ disabled/aria-busy ban đầu, đặt trạng thái chờ và trả hàm
   khôi phục. Không thay innerHTML, không mất nhãn/icon/listener của nút.
2. app.js → click delegation chặn phần tử disabled/aria-busy, dùng helper trước handleAction và
   khôi phục trong finally. Theme/menu/password/pause/đóng dialog là thao tác tức thời, không bị
   disable thoáng qua. Các điều kiện quyền/ready/mạng/slot vẫn do nghiệp vụ quyết định như trước.
3. app.js → submit delegation vẫn dùng form.dataset.busy chặn Enter/submit lặp; helper khóa nút
   submit và khôi phục trong finally cả khi validation/API lỗi. Giữ trạng thái ban đầu thay vì
   luôn mở lại một nút vốn disabled từ trước.
4. app.css → token control-disabled-*, vùng theme sáng/tối và mục 11 quản lý trạng thái. Vòng chờ
   dùng pseudo-element, không phát request/tạo timer JS. Reduced motion chung vẫn áp dụng.

## Kiểm chứng

Harness tạm build/verification/button-states.cjs dùng Playwright/Chromium headless riêng, HTTP server
loopback phục vụ app.html/CSS/module thật, chỉ mock /auth/session và POST /login bằng dữ liệu giả.
Không mở tab/profile của người dùng, không chạy Java hoặc truy cập Neon. Không thêm Node/package.json
hoặc dependency frontend vào source/CI; harness và ảnh dưới build bị ignore.

- Kiểm primary/secondary/danger/text/icon: enabled/disabled/busy ở cả dark/light, opacity, cursor và
  palette sau khi transition kết thúc; hover/active của primary trong hero/dialog vẫn chữ tối trên nền lime.
- Chạy app.js thực: form login request chậm rồi 401; click/Enter/requestSubmit liên tiếp chỉ gửi một
  request, aria-busy được gỡ và nút mở lại, nhãn ban đầu và thông báo lỗi đúng.
- Đổi theme bằng nút thật không disable/chớp loading. Thao tác mở xác nhận rồi Quay lại khôi phục nút.
- Không có pageerror JavaScript; đã xem ảnh buttons-after-dark.png/buttons-after-light.png.

Kết quả đóng gói/kiểm source mới nhất xem [STATUS](STATUS.md). Đây là kiểm hiển thị và xử lý sự kiện
trên trình duyệt với API giả; không thay thế nghiệm thu auth/multiplayer/thiết bị cảm ứng thật.

Sau khi chạy bản mới, nếu Service Worker đang giữ UI cũ, rời trận rồi chọn nút cập nhật giao diện
và tải lại. processResources đổi fingerprint tự động theo nội dung CSS/JS; không ép reload giữa trận.
