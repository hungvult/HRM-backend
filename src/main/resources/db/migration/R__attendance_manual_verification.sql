-- UC009/010: Wi-Fi hoặc GPS; chỉ lưu các nhóm bằng chứng đã được service xác minh.
-- NOT VALID giữ dữ liệu lịch sử, vẫn kiểm tra INSERT/UPDATE mới. Không tự sửa/xóa bảng công.
-- Repeatable bổ sung đúng cột liên quan khi Production chưa có V4; không dựa vào V5 hoặc chạy lại toàn bộ schema.
DO $$
BEGIN
    IF to_regclass('public.attendance_records') IS NULL THEN RETURN; END IF;
    ALTER TABLE public.attendance_records
        ADD COLUMN IF NOT EXISTS check_in_work_location_id BIGINT,
        ADD COLUMN IF NOT EXISTS check_in_wifi_network_id BIGINT,
        ADD COLUMN IF NOT EXISTS check_in_wifi_ssid VARCHAR(255),
        ADD COLUMN IF NOT EXISTS check_in_latitude NUMERIC(9,6),
        ADD COLUMN IF NOT EXISTS check_in_longitude NUMERIC(9,6),
        ADD COLUMN IF NOT EXISTS check_in_location_distance_meters INTEGER,
        ADD COLUMN IF NOT EXISTS check_in_gps_accuracy_meters NUMERIC(8,2),
        ADD COLUMN IF NOT EXISTS check_in_location_captured_at TIMESTAMPTZ,
        ADD COLUMN IF NOT EXISTS check_out_work_location_id BIGINT,
        ADD COLUMN IF NOT EXISTS check_out_wifi_network_id BIGINT,
        ADD COLUMN IF NOT EXISTS check_out_wifi_ssid VARCHAR(255),
        ADD COLUMN IF NOT EXISTS check_out_latitude NUMERIC(9,6),
        ADD COLUMN IF NOT EXISTS check_out_longitude NUMERIC(9,6),
        ADD COLUMN IF NOT EXISTS check_out_location_distance_meters INTEGER,
        ADD COLUMN IF NOT EXISTS check_out_gps_accuracy_meters NUMERIC(8,2),
        ADD COLUMN IF NOT EXISTS check_out_location_captured_at TIMESTAMPTZ,
        ADD COLUMN IF NOT EXISTS is_manually_adjusted BOOLEAN NOT NULL DEFAULT false,
        ADD COLUMN IF NOT EXISTS adjusted_by_account_id BIGINT,
        ADD COLUMN IF NOT EXISTS adjustment_reason TEXT;
    ALTER TABLE public.attendance_records
        DROP CONSTRAINT IF EXISTS ck_attendance_check_in_verification,
        DROP CONSTRAINT IF EXISTS ck_attendance_check_out_verification,
        DROP CONSTRAINT IF EXISTS ck_attendance_manual_adjustment_audit;
    -- Chỉnh công được phép thiếu bằng chứng, nhưng bắt buộc có người thao tác và lý do.
    ALTER TABLE public.attendance_records
        ADD CONSTRAINT ck_attendance_manual_adjustment_audit CHECK (
            is_manually_adjusted IS NOT TRUE OR (adjusted_by_account_id IS NOT NULL AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL)
        ) NOT VALID,
        ADD CONSTRAINT ck_attendance_check_in_verification CHECK (
       check_in_at IS NULL OR (is_manually_adjusted IS TRUE AND adjusted_by_account_id IS NOT NULL AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL) OR (
         check_in_work_location_id IS NOT NULL
         AND ((check_in_wifi_network_id IS NULL AND check_in_wifi_ssid IS NULL)
           OR (check_in_wifi_network_id IS NOT NULL AND NULLIF(btrim(check_in_wifi_ssid), '') IS NOT NULL))
         AND ((check_in_latitude IS NULL AND check_in_longitude IS NULL AND check_in_gps_accuracy_meters IS NULL AND check_in_location_captured_at IS NULL AND check_in_location_distance_meters IS NULL) OR (check_in_latitude IS NOT NULL AND check_in_longitude IS NOT NULL AND check_in_gps_accuracy_meters IS NOT NULL AND check_in_location_captured_at IS NOT NULL AND check_in_location_distance_meters IS NOT NULL AND check_in_latitude BETWEEN -90 AND 90 AND check_in_longitude BETWEEN -180 AND 180 AND check_in_gps_accuracy_meters > 0 AND check_in_location_distance_meters >= 0))
         AND (check_in_wifi_network_id IS NOT NULL OR (check_in_latitude IS NOT NULL AND check_in_longitude IS NOT NULL AND check_in_gps_accuracy_meters IS NOT NULL AND check_in_location_captured_at IS NOT NULL AND check_in_location_distance_meters IS NOT NULL AND check_in_latitude BETWEEN -90 AND 90 AND check_in_longitude BETWEEN -180 AND 180 AND check_in_gps_accuracy_meters > 0 AND check_in_location_distance_meters >= 0))
       )
    ) NOT VALID,
        ADD CONSTRAINT ck_attendance_check_out_verification CHECK (
       check_out_at IS NULL OR (is_manually_adjusted IS TRUE AND adjusted_by_account_id IS NOT NULL AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL) OR (
         check_out_work_location_id IS NOT NULL
         AND ((check_out_wifi_network_id IS NULL AND check_out_wifi_ssid IS NULL)
           OR (check_out_wifi_network_id IS NOT NULL AND NULLIF(btrim(check_out_wifi_ssid), '') IS NOT NULL))
         AND ((check_out_latitude IS NULL AND check_out_longitude IS NULL AND check_out_gps_accuracy_meters IS NULL AND check_out_location_captured_at IS NULL AND check_out_location_distance_meters IS NULL) OR (check_out_latitude IS NOT NULL AND check_out_longitude IS NOT NULL AND check_out_gps_accuracy_meters IS NOT NULL AND check_out_location_captured_at IS NOT NULL AND check_out_location_distance_meters IS NOT NULL AND check_out_latitude BETWEEN -90 AND 90 AND check_out_longitude BETWEEN -180 AND 180 AND check_out_gps_accuracy_meters > 0 AND check_out_location_distance_meters >= 0))
         AND (check_out_wifi_network_id IS NOT NULL OR (check_out_latitude IS NOT NULL AND check_out_longitude IS NOT NULL AND check_out_gps_accuracy_meters IS NOT NULL AND check_out_location_captured_at IS NOT NULL AND check_out_location_distance_meters IS NOT NULL AND check_out_latitude BETWEEN -90 AND 90 AND check_out_longitude BETWEEN -180 AND 180 AND check_out_gps_accuracy_meters > 0 AND check_out_location_distance_meters >= 0))
       )
    ) NOT VALID;
END $$;
