-- Phase 3: chấm công chỉ hợp lệ khi backend xác minh đồng thời Wi-Fi công ty
-- và GPS nằm trong phạm vi địa điểm làm việc được cấu hình.
--
-- Migration chỉ bổ sung cấu trúc/ràng buộc, không xóa hay sửa dữ liệu lịch sử.
-- Các constraint NOT VALID vẫn được áp dụng cho bản ghi thêm mới/cập nhật sau migration;
-- dữ liệu lịch sử được rà soát và xử lý riêng trước khi VALIDATE CONSTRAINT.

-- Danh mục địa điểm và mạng Wi-Fi cần cho xác minh check-in/check-out.
-- IF NOT EXISTS giúp V4 tương thích với môi trường test đã có dữ liệu này từ schema cũ.
CREATE TABLE IF NOT EXISTS work_locations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    address TEXT,
    latitude NUMERIC(9,6) NOT NULL,
    longitude NUMERIC(9,6) NOT NULL,
    allowed_radius_meters INTEGER NOT NULL DEFAULT 100,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_work_locations_code CHECK (code = upper(code)),
    CONSTRAINT ck_work_locations_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_work_locations_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_work_locations_radius CHECK (allowed_radius_meters > 0)
);

CREATE TABLE IF NOT EXISTS work_location_wifi_networks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    work_location_id BIGINT NOT NULL REFERENCES work_locations(id) ON DELETE RESTRICT,
    ssid VARCHAR(255) NOT NULL,
    bssid VARCHAR(17),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_work_location_wifi_bssid
        CHECK (bssid IS NULL OR bssid ~* '^([0-9A-F]{2}:){5}([0-9A-F]{2})$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_work_location_wifi_ssid_bssid
    ON work_location_wifi_networks (lower(ssid), COALESCE(lower(bssid), ''));
CREATE INDEX IF NOT EXISTS ix_work_location_wifi_active
    ON work_location_wifi_networks (work_location_id)
    WHERE is_active;

-- Bảng công cá nhân theo nhân viên và ngày công.
CREATE INDEX IF NOT EXISTS ix_attendance_employee_work_date
    ON attendance_records (employee_id, work_date DESC);

-- Một nhân viên chỉ có một bản ghi công trong một ngày công.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_attendance_employee_work_date'
          AND conrelid = 'attendance_records'::regclass
    ) THEN
        ALTER TABLE attendance_records
            ADD CONSTRAINT uq_attendance_employee_work_date
            UNIQUE (employee_id, work_date);
    END IF;
END $$;

-- Không cho phép nhân viên tự duyệt đơn nghỉ phép của chính mình.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_leave_request_not_self_reviewed'
          AND conrelid = 'leave_requests'::regclass
    ) THEN
        ALTER TABLE leave_requests
            ADD CONSTRAINT ck_leave_request_not_self_reviewed
            CHECK (reviewed_by_employee_id IS NULL OR reviewed_by_employee_id <> employee_id)
            NOT VALID;
    END IF;
END $$;

-- Lưu mạng Wi-Fi đã được backend xác minh, tọa độ, chất lượng và thời điểm
-- thiết bị lấy GPS tại từng thời điểm check-in/check-out. SSID và tọa độ là
-- snapshot phục vụ audit; server vẫn là nguồn quyết định thời điểm chấm công.
ALTER TABLE attendance_records
    ADD COLUMN IF NOT EXISTS check_in_work_location_id BIGINT,
    ADD COLUMN IF NOT EXISTS check_out_work_location_id BIGINT,
    ADD COLUMN IF NOT EXISTS check_in_wifi_network_id BIGINT,
    ADD COLUMN IF NOT EXISTS check_out_wifi_network_id BIGINT,
    ADD COLUMN IF NOT EXISTS check_in_location_distance_meters INTEGER,
    ADD COLUMN IF NOT EXISTS check_out_location_distance_meters INTEGER,
    ADD COLUMN IF NOT EXISTS check_in_gps_accuracy_meters NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS check_in_location_captured_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS check_out_gps_accuracy_meters NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS check_out_location_captured_at TIMESTAMP WITH TIME ZONE;

-- Cho phép khóa ngoại kép bảo đảm mạng Wi-Fi thuộc đúng địa điểm đã ghi nhận.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_work_location_wifi_network_location'
          AND conrelid = 'work_location_wifi_networks'::regclass
    ) THEN
        ALTER TABLE work_location_wifi_networks
            ADD CONSTRAINT uq_work_location_wifi_network_location
            UNIQUE (id, work_location_id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_attendance_check_in_wifi_network_location'
          AND conrelid = 'attendance_records'::regclass
    ) THEN
        ALTER TABLE attendance_records
            ADD CONSTRAINT fk_attendance_check_in_wifi_network_location
            FOREIGN KEY (check_in_wifi_network_id, check_in_work_location_id)
            REFERENCES work_location_wifi_networks (id, work_location_id)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_attendance_check_out_wifi_network_location'
          AND conrelid = 'attendance_records'::regclass
    ) THEN
        ALTER TABLE attendance_records
            ADD CONSTRAINT fk_attendance_check_out_wifi_network_location
            FOREIGN KEY (check_out_wifi_network_id, check_out_work_location_id)
            REFERENCES work_location_wifi_networks (id, work_location_id)
            ON DELETE RESTRICT
            NOT VALID;
    END IF;
END $$;

-- Khi có mốc chấm công, phải có Wi-Fi đã xác minh và GPS hợp lệ.
-- DB lưu các điều kiện nền tảng; kiểm tra IP/CIDR, Wi-Fi active, Haversine,
-- bán kính và phạm vi quản lý trực tiếp được service thực hiện trong transaction.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_attendance_check_in_verification'
          AND conrelid = 'attendance_records'::regclass
    ) THEN
        ALTER TABLE attendance_records
            ADD CONSTRAINT ck_attendance_check_in_verification
            CHECK (
                check_in_at IS NULL
                OR (
                    NULLIF(btrim(check_in_wifi_ssid), '') IS NOT NULL
                    AND check_in_wifi_network_id IS NOT NULL
                    AND check_in_work_location_id IS NOT NULL
                    AND check_in_latitude IS NOT NULL
                    AND check_in_latitude BETWEEN -90 AND 90
                    AND check_in_longitude IS NOT NULL
                    AND check_in_longitude BETWEEN -180 AND 180
                    AND check_in_location_distance_meters IS NOT NULL
                    AND check_in_location_distance_meters >= 0
                    AND check_in_gps_accuracy_meters IS NOT NULL
                    AND check_in_gps_accuracy_meters > 0
                    AND check_in_location_captured_at IS NOT NULL
                )
            )
            NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_attendance_check_out_verification'
          AND conrelid = 'attendance_records'::regclass
    ) THEN
        ALTER TABLE attendance_records
            ADD CONSTRAINT ck_attendance_check_out_verification
            CHECK (
                check_out_at IS NULL
                OR (
                    NULLIF(btrim(check_out_wifi_ssid), '') IS NOT NULL
                    AND check_out_wifi_network_id IS NOT NULL
                    AND check_out_work_location_id IS NOT NULL
                    AND check_out_latitude IS NOT NULL
                    AND check_out_latitude BETWEEN -90 AND 90
                    AND check_out_longitude IS NOT NULL
                    AND check_out_longitude BETWEEN -180 AND 180
                    AND check_out_location_distance_meters IS NOT NULL
                    AND check_out_location_distance_meters >= 0
                    AND check_out_gps_accuracy_meters IS NOT NULL
                    AND check_out_gps_accuracy_meters > 0
                    AND check_out_location_captured_at IS NOT NULL
                )
            )
            NOT VALID;
    END IF;
END $$;
