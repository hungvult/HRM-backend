# HRM Backend

Backend service for the HRM system, built with Spring Boot, Maven, Java 21, Spring Security, Spring Data JPA, and PostgreSQL.

## Requirements

- Java 21
- PostgreSQL 14 or newer
- Git
- Maven is optional because the project includes Maven Wrapper scripts

## Setup

Clone the repository and move into the backend folder:

```bash
git clone https://github.com/hungvult/HRM-backend.git
cd HRM-backend
```

Create a local environment file:

```bash
cp .env.example .env
```

Update `.env` for your local PostgreSQL database:

```env
DB_URL=jdbc:postgresql://localhost:5432/hrm
DB_USERNAME=postgres
DB_PASSWORD=1234
```

Make sure the database exists before starting the app:

```sql
CREATE DATABASE hrm;
```

## Run Locally

On Windows:

```bash
mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
./mvnw spring-boot:run
```

The API runs on:

```text
http://localhost:8080
```

## Build

On Windows:

```bash
mvnw.cmd clean package
```

On macOS or Linux:

```bash
./mvnw clean package
```

The generated JAR is created in the `target` folder.

## Test

On Windows:

```bash
mvnw.cmd test
```

On macOS or Linux:

```bash
./mvnw test
```

## Environment Variables

| Name          | Description                    | Default                                |
| ------------- | ------------------------------ | -------------------------------------- |
| `DB_URL`      | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/hrm` |
| `DB_USERNAME` | PostgreSQL username            | `postgres`                             |
| `DB_PASSWORD` | PostgreSQL password            | Required                               |
| `APP_ATTENDANCE_TRUSTED_SOURCES_0_CIDR` | CIDR/IP egress của Wi-Fi công ty | Required for attendance |
| `APP_ATTENDANCE_TRUSTED_SOURCES_0_WIFI_NETWORK_ID` | ID mạng Wi-Fi đang hoạt động trong database | Required for attendance |
| `APP_ATTENDANCE_TRUSTED_PROXIES_0_CIDR` | IP/CIDR riêng của reverse proxy đáng tin cậy | Optional |

## Attendance behind Nginx

Khi backend chạy sau Nginx, chỉ cấu hình `APP_ATTENDANCE_TRUSTED_PROXIES_0_CIDR` bằng IP hoặc CIDR dành riêng cho Nginx. Backend chỉ đọc `X-Forwarded-For` từ proxy này; request gửi trực tiếp không thể tự giả mạo header.

Nginx phải thay thế, không nối thêm, header địa chỉ client:

```nginx
proxy_set_header X-Forwarded-For $remote_addr;
proxy_set_header X-Real-IP $remote_addr;
```

Nếu Nginx nằm sau load balancer, cần cấu hình `real_ip_header` và `set_real_ip_from` cho load balancer trước khi dùng `$remote_addr`. Backend nên chỉ mở cổng nội bộ cho Nginx.

Trước khi bàn giao test, DevOps cần cấu hình CIDR egress thật và `wifiNetworkId` khớp một bản ghi `work_location_wifi_networks` đang active, có `work_locations` đang active với tọa độ và bán kính hợp lệ. Không dùng giá trị ví dụ trong `.env.example` cho staging/production.

## Mobile SRS API alignment (24/11/2026)

Các đường dẫn dưới đây là path controller, sau API prefix do gateway triển khai. Mọi thao tác cá nhân lấy tài khoản từ bearer; không nhận ID người dùng thay thế từ client.

| API | Hợp đồng và tương thích |
| --- | --- |
| `POST /v1/attendance/check-in`, `/check-out` | Chấp nhận `{}` khi không có GPS. Nếu gửi GPS phải đủ `latitude`, `longitude`, `accuracyMeters`, `locationCapturedAt`; giá trị không hợp lệ trả 400. `APP_ATTENDANCE_VERIFICATION_MODE=AND` mặc định giữ yêu cầu Wi-Fi VÀ GPS. CD chọn `OR` khi phát hành: Wi-Fi đúng hoặc GPS đúng được chấm công. `APP_ATTENDANCE_WIFI_VERIFICATION_ENABLED=false` giữ GPS-only cũ. Chỉ lưu nhóm bằng chứng thực sự xác minh được. |
| `POST /v1/leave-requests` | JSON cũ giữ nguyên. Thêm multipart: part `request` là JSON có MIME **application/json**, bind trực tiếp `@Valid CreateLeaveRequest`; part `attachment` tùy chọn, tối đa một ảnh JPG/PNG/WebP. Mobile native có thể gửi JSON như một file part data URI; không gửi part request dưới dạng text/plain. |
| `GET /v1/leave-requests/{id}` | Có `attachment: {fileName, contentType, sizeBytes, url}` nếu có ảnh; bỏ trường nếu không có. URL nội bộ `/v1/leave-requests/{id}/attachment`, vẫn cần bearer. API danh sách không nhúng ảnh/metadata. |
| `GET /v1/leave-requests/{id}/attachment` | Cùng quyền xem đơn: chủ đơn, HR/Admin hoặc quản lý đúng phạm vi hiện có. Trả ảnh nhị phân với `private, no-store`, `nosniff`. Thiếu ảnh trả 404 `LEAVE_ATTACHMENT_NOT_FOUND`; ngoài phạm vi giữ lỗi 403 cũ. |
| `POST /v1/leave-requests/{id}/cancel` | Body vắng giữ tương thích; có thể gửi `{ "reason": "..." }`, tối đa 1.000 ký tự. Trim, trắng thành null; lưu history.note cùng transaction PENDING → CANCELLED. Khác chủ 403, không có 404, đã xử lý 409. |
| `PATCH /v1/notifications/{id}/unread` | Chỉ đúng người nhận; trả NotificationResponse hiện có với `read=false`, `readAt=null`. Gọi lặp không tạo sự kiện mới; sai người nhận/ID trả 404. `read-all` đọc lại số chưa đọc từ DB thay vì đoán 0. |
| `GET /v1/payroll/my-payslips/by-period?year=2026&month=10` | Giữ **đúng MyPayslipResponse của API chi tiết cũ**: `{payrollRunId, year, month, item, publishedAt}`. Route tĩnh ưu tiên trước `/{runId}`. `year` 2000–2100, `month` 1–12. 404 `PAYSLIP_NOT_FOUND`: thiếu hồ sơ/kỳ/phiếu của chính nhân viên; 404 `PAYSLIP_NOT_AVAILABLE`: kỳ chưa PUBLISHED, không truy vấn/trả tiền. API danh sách và chi tiết theo runId giữ nguyên. |

Phép `leave_types.is_paid=false` không yêu cầu/quản lý leave_balances khi gửi và duyệt; phản hồi duyệt có `remainingDays: null` vì không có quỹ áp dụng. Phép hưởng lương giữ khóa quỹ, trừ PENDING khi gửi và chỉ tăng used_days lúc duyệt. Web cần coi remainingDays là nullable ở loại không lương.

Ảnh tối đa **5 MiB = 5 × 1024 × 1024 = 5.242.880 byte**. Spring giới hạn tổng multipart **6 MiB = 6.291.456 byte** để chứa JSON/header/boundary. Nginx hiệu lực phải có `client_max_body_size 6m;` hoặc lớn hơn; kiểm tra cả cấu hình global/inherited trước khi chỉnh. Mã nguồn này không thay cấu hình VPS. Kiểm tra MIME cùng chữ ký/header ảnh và tên an toàn trước khi tạo đơn; mã lỗi `LEAVE_ATTACHMENT_INVALID` (400), `LEAVE_ATTACHMENT_TOO_LARGE` (413), part request sai MIME (415). Ảnh lưu `bytea` trong bảng riêng, PK/FK leave_request_id giới hạn một ảnh/đơn và rollback cùng đơn/lịch sử/thông báo. Log bỏ qua body multipart/binary, vẫn ghi metadata HTTP và JSON thông thường.

Hai repeatable migration cập nhật đúng cấu trúc liên quan khi môi trường chưa có V4/V5: `R__attendance_manual_verification.sql`, `R__leave_request_attachments.sql`. Không sửa các versioned migration đã phát hành, không chạy lại schema, không biến đổi dữ liệu lịch sử. Constraint attendance `NOT VALID` giữ hàng lịch sử và kiểm tra các lần ghi mới; chỉnh công vẫn bắt buộc actor + lý do. Triển khai backend/migration và cấu hình proxy trước khi Mobile gửi `{}` hoặc multipart.

Kiểm tra ảnh dùng decoder thật sau kiểm tra chữ ký: ImageIO chuẩn cho JPG/PNG và [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) `imageio-webp:3.15.3` cho WebP (bản release trong [metadata Maven Central](https://repo.maven.apache.org/maven2/com/twelvemonkeys/imageio/imageio-webp/maven-metadata.xml), xác minh ngày 24/11/2026). Đọc kích thước trước khi giải mã và dùng sourceSubsampling để preview có cạnh tối đa 1.024 pixel, giữ nguyên ảnh gốc trong DB. Giới hạn nguồn: **JPG/PNG tối đa 200.000.000 pixel; WebP tối đa 25.000.000 pixel; mỗi cạnh tối đa 65.535 pixel**. WebP cần giới hạn riêng vì decoder có bộ đệm nguồn kể cả subsampling. Vượt giới hạn trả 400 `LEAVE_ATTACHMENT_DIMENSIONS_TOO_LARGE` với thông báo nêu giới hạn, ảnh không giải mã được trả 400 `LEAVE_ATTACHMENT_INVALID`, tất cả trước khi ghi DB.

Kiểm thử cục bộ không cần DB/API thật: `mvnw.cmd -Dtest=!BackendApplicationTests test`. Test context đầy đủ `BackendApplicationTests` cần PostgreSQL và được để ngoài lệnh này; test SQL tự bỏ qua nếu thiếu `DB_URL`. Trên JDK 26, Mockito cần `-DargLine="-javaagent:<path-to-mockito-core-5.20.0.jar> -Dnet.bytebuddy.experimental=true"` để tránh self-attach bị chặn. Race trong test service dùng khóa repository mô phỏng; không chứng minh khóa PostgreSQL hoặc hiệu năng dưới tải thực tế.

## Troubleshooting

- If the app cannot connect to PostgreSQL, confirm that PostgreSQL is running and the `hrm` database exists.
- If Java compilation fails, confirm that `java -version` reports Java 21.
- If dependencies fail to download, check your network connection and retry the Maven command.
