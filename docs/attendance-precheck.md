# Kiểm tra ngầm điều kiện chấm công

- Khi màn chấm công đang mở và App ở foreground, App gửi GPS mới tới `POST /api/v1/attendance/pre-check`.
- Kiểm tra khi vào màn, đổi mạng, trở lại App, có thay đổi vị trí (giới hạn tần suất) và mỗi 30 giây.
- App chỉ đọc quyền GPS hiện có; không tự mở hộp thoại xin quyền. Không lấy được GPS trong 6 giây thì gửi `{}`.
- Body GPS gồm `latitude`, `longitude`, `accuracyMeters`, `locationCapturedAt`; phải có đủ cả nhóm hoặc bỏ cả nhóm.
- Backend lấy tài khoản từ phiên và IP qua bộ xác minh proxy tin cậy hiện có. Không nhận employee ID hoặc IP do App tự khai.
- Backend đọc hồ sơ nhân viên, địa điểm và cấu hình mạng đang hoạt động; dùng cùng chính sách AND/OR/GPS-only với check-in/out. Cấu hình triển khai hiện tại là OR.
- Kết quả 200 gồm `eligible`, `verificationMode`, `wifiVerified`, `gpsVerified`, `verifiedAt`. Lỗi giữ mã nghiệp vụ hiện có; 400 cho nhóm GPS không hợp lệ, 401 cho phiên không hợp lệ, 403 cho điều kiện không đạt.
- API không đọc/ghi bảng công, không khóa nhân viên và không cấp quyền cho lần ghi công sau. Logging HTTP vẫn chạy theo cơ chế chung.
- App không hiển thị kết quả/lỗi ngầm, không thay đổi giao diện hoặc nút. Lúc bấm check-in/out vẫn lấy bằng chứng mới và Backend xác minh lại trước khi ghi công.
- Rời màn, chạy nền, đổi mạng sẽ hủy yêu cầu cũ. Chỉ giữ một lượt đang chạy và một lượt chờ mới nhất, không tự retry thao tác ghi công.
- Mobile cần cài build mới để có hành vi gửi ngầm; các build cũ vẫn chấm công theo API hiện có.

Thư viện đã có sẵn: [Expo Location v57](https://docs.expo.dev/versions/v57.0.0/sdk/location/), [Expo Network v57](https://docs.expo.dev/versions/v57.0.0/sdk/network/). Không thêm quyền định vị nền.
