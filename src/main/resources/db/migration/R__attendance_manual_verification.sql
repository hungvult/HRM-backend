-- Manual attendance is created/adjusted only by the protected management API.
-- Keep the server-controlled flag, actor and reason together; never invent GPS
-- evidence for a correction. Self-service check-in/out still requires evidence.
-- Repeatable so this also works when production has not received V4/V5 yet.
DO $$
BEGIN
    -- A fresh legacy installation may create its tables through Hibernate later.
    IF to_regclass('public.attendance_records') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE public.attendance_records
        DROP CONSTRAINT IF EXISTS ck_attendance_check_in_verification,
        DROP CONSTRAINT IF EXISTS ck_attendance_check_out_verification,
        DROP CONSTRAINT IF EXISTS ck_attendance_manual_adjustment_audit;

    ALTER TABLE public.attendance_records
        ADD CONSTRAINT ck_attendance_manual_adjustment_audit CHECK (
            is_manually_adjusted IS NOT TRUE
            OR (
                adjusted_by_account_id IS NOT NULL
                AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL
            )
        ) NOT VALID,
        ADD CONSTRAINT ck_attendance_check_in_verification CHECK (
            check_in_at IS NULL
            OR (
                is_manually_adjusted IS TRUE
                AND adjusted_by_account_id IS NOT NULL
                AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL
            )
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
        ) NOT VALID,
        ADD CONSTRAINT ck_attendance_check_out_verification CHECK (
            check_out_at IS NULL
            OR (
                is_manually_adjusted IS TRUE
                AND adjusted_by_account_id IS NOT NULL
                AND NULLIF(btrim(adjustment_reason), '') IS NOT NULL
            )
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
        ) NOT VALID;
END $$;
