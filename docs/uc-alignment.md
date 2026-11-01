# Hợp đồng bổ sung theo UC

- `PATCH /v1/accounts/{id}` nhận thêm `roles` tùy chọn. Có trường này thì lưu thông tin và role cùng transaction; bỏ trường thì giữ role. Role thay đổi làm hết hiệu lực các phiên đích. Username/email/role lỗi không lưu một phần.
- `POST /v1/employees` nhận `employeeCode` tùy chọn. Web yêu cầu nhập; client cũ bỏ trường vẫn tự sinh mã, tránh mã đã nhập tay. Trùng mã trả409 với `errors.field=employeeCode`.
- Form phòng ban/chức vụ gửi `code`, `status` cùng tên/mô tả/cấp bậc. Client cũ bỏ trường khi sửa giữ giá trị hiện tại; tạo mới mặc định ACTIVE. Chức vụ bỏ mã khi tạo vẫn tự sinh.
- Vô hiệu hóa giữ phân công hiện tại. Phân công mới kiểm trạng thái dưới cùng khóa danh mục.
- `DELETE /v1/departments/{id}` và `/v1/positions/{id}` chỉ Admin/HR. Còn phân công hiện hành trả409; nếu trống thì xóa mềm, ẩn khỏi danh mục nhưng lịch sử vẫn đọc được FK.
- Phân công nhiều lần cùng ngày đóng bản cũ vào chính ngày đó, giữ từng bản lịch sử. Các truy vấn quyền, bảng công và lương chọn bản hiệu lực cuối theo ngày rồi ID.
- Danh sách duyệt phép của quản lý chỉ gồm nhân viên trực thuộc; `/me/leave-requests` và quyền chủ đơn ở chi tiết vẫn giữ nguyên. HR xem/duyệt toàn công ty.
- `POST /v1/payroll/previews` nhận thêm `regenerateRunId` tùy chọn. Phải đúng kỳ, bảng GENERATED. Preview lưu revision/fingerprint/chủ sở hữu/hạn dùng. Xác nhận bằng endpoint cũ, token chỉ được ghi một lần; tạo lại tăng revision cùng transaction. PUBLISHED trả409, không thay snapshot.
- V6/V7 bổ sung xóa mềm, kiểm thứ tự ngày và dữ liệu preview tạo lại. Cần backup trước triển khai. Chạy cả schema trống và bản clone dữ liệu cũ để xác minh migration.
- Mã tiền lương vẫn tính cả lương/phụ cấp/khấu trừ từ phút công. Sửa dữ liệu demo cũ dùng snapshot của chính kỳ, backup và audit riêng; không lấy hồ sơ lương hiện tại để viết lại lịch sử.
