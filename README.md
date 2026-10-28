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

## Troubleshooting

- If the app cannot connect to PostgreSQL, confirm that PostgreSQL is running and the `hrm` database exists.
- If Java compilation fails, confirm that `java -version` reports Java 21.
- If dependencies fail to download, check your network connection and retry the Maven command.
