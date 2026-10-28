-- Payroll MVP (UC016-UC019): lưu snapshot bất biến và chặn dữ liệu mới trái quy tắc.
-- Migration chỉ bổ sung. Các cột revision/status legacy được giữ để không mất lịch sử.

-- 1. Hồ sơ lương: ngày đầu tháng, tiền VND nguyên, không có khoảng hiệu lực chồng lấn.
ALTER TABLE employee_salary_profiles
    ADD CONSTRAINT ck_salary_profile_effective_from_month_start
        CHECK (EXTRACT(DAY FROM effective_from) = 1) NOT VALID,
    ADD CONSTRAINT ck_salary_profile_effective_to_month_end
        CHECK (
            effective_to IS NULL
            OR effective_to = (date_trunc('month', effective_to) + INTERVAL '1 month - 1 day')::date
        ) NOT VALID,
    ADD CONSTRAINT ck_salary_profile_vnd_amounts_integral
        CHECK (
            base_salary = trunc(base_salary)
            AND fixed_allowance = trunc(fixed_allowance)
            AND fixed_deduction = trunc(fixed_deduction)
        ) NOT VALID,
    ADD CONSTRAINT ck_salary_profile_vnd_amounts_limit
        CHECK (
            base_salary BETWEEN 1 AND 999999999999
            AND fixed_allowance BETWEEN 0 AND 999999999999
            AND fixed_deduction BETWEEN 0 AND 999999999999
        ) NOT VALID;

CREATE UNIQUE INDEX uq_salary_profile_employee_effective_from
    ON employee_salary_profiles (employee_id, effective_from);

CREATE OR REPLACE FUNCTION prevent_salary_profile_overlap()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM employee_salary_profiles existing_profile
        WHERE existing_profile.employee_id = NEW.employee_id
          AND existing_profile.id IS DISTINCT FROM NEW.id
          AND daterange(
                existing_profile.effective_from,
                COALESCE(existing_profile.effective_to, 'infinity'::date),
                '[]'
              ) && daterange(
                NEW.effective_from,
                COALESCE(NEW.effective_to, 'infinity'::date),
                '[]'
              )
    ) THEN
        RAISE EXCEPTION 'Salary profile date range overlaps an existing profile for employee %', NEW.employee_id
            USING ERRCODE = '23P01';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_salary_profile_prevent_overlap
BEFORE INSERT OR UPDATE OF employee_id, effective_from, effective_to
ON employee_salary_profiles
FOR EACH ROW EXECUTE FUNCTION prevent_salary_profile_overlap();

-- Mức lương từng được dùng chỉ có thể đóng sau kỳ được tham chiếu; không đổi tiền/mốc bắt đầu.
CREATE OR REPLACE FUNCTION prevent_referenced_salary_profile_mutation()
RETURNS TRIGGER AS $$
DECLARE
    latest_referenced_period_end DATE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payroll_items WHERE salary_profile_id = OLD.id) THEN
        RETURN NEW;
    END IF;

    IF NEW.employee_id IS DISTINCT FROM OLD.employee_id
       OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
       OR NEW.base_salary IS DISTINCT FROM OLD.base_salary
       OR NEW.fixed_allowance IS DISTINCT FROM OLD.fixed_allowance
       OR NEW.fixed_deduction IS DISTINCT FROM OLD.fixed_deduction THEN
        RAISE EXCEPTION 'Salary profile % is referenced by a payroll item and cannot change payroll inputs', OLD.id
            USING ERRCODE = '23503';
    END IF;

    IF NEW.effective_to IS DISTINCT FROM OLD.effective_to THEN
        SELECT MAX((make_date(run.payroll_year, run.payroll_month, 1) + INTERVAL '1 month - 1 day')::date)
        INTO latest_referenced_period_end
        FROM payroll_items item
        JOIN payroll_runs run ON run.id = item.payroll_run_id
        WHERE item.salary_profile_id = OLD.id;

        IF NEW.effective_to IS NOT NULL AND NEW.effective_to < latest_referenced_period_end THEN
            RAISE EXCEPTION 'Salary profile % cannot be closed before a referenced payroll period', OLD.id
                USING ERRCODE = '23503';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_salary_profile_prevent_referenced_mutation
BEFORE UPDATE ON employee_salary_profiles
FOR EACH ROW EXECUTE FUNCTION prevent_referenced_salary_profile_mutation();

-- 2. Bảng lương: snapshot C/M, phiên bản quy tắc và fingerprint của preview.
ALTER TABLE payroll_runs
    ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS supersedes_payroll_run_id BIGINT REFERENCES payroll_runs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS is_current BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS standard_working_days INTEGER,
    ADD COLUMN IF NOT EXISTS standard_minutes_per_day INTEGER,
    ADD COLUMN IF NOT EXISTS calculation_rule_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS input_fingerprint VARCHAR(128),
    ADD CONSTRAINT ck_payroll_run_public_status
        CHECK (status IN ('GENERATED', 'PUBLISHED')) NOT VALID,
    ADD CONSTRAINT ck_payroll_run_standard_working_days
        CHECK (standard_working_days IS NULL OR standard_working_days > 0) NOT VALID,
    ADD CONSTRAINT ck_payroll_run_standard_minutes
        CHECK (standard_minutes_per_day IS NULL OR standard_minutes_per_day > 0) NOT VALID,
    ADD CONSTRAINT ck_payroll_run_mvp_legacy_fields
        CHECK (
            revision = 1
            AND is_current = TRUE
            AND supersedes_payroll_run_id IS NULL
            AND superseded_at IS NULL
        ) NOT VALID;

ALTER TABLE payroll_runs
    ALTER COLUMN status SET DEFAULT 'GENERATED',
    ALTER COLUMN revision SET DEFAULT 1,
    ALTER COLUMN is_current SET DEFAULT TRUE;

-- Một kỳ chỉ có một bảng chính thức. Status legacy đã có vẫn được giữ nguyên, nhưng không được tạo mới.
CREATE UNIQUE INDEX uq_payroll_mvp_period
    ON payroll_runs (payroll_year, payroll_month)
    WHERE status IN ('GENERATED', 'PUBLISHED')
      AND revision = 1
      AND is_current = TRUE;

-- 3. Dòng lương: snapshot nhân viên/phòng ban, khoản đầu vào và phút công Q chính xác.
ALTER TABLE payroll_items
    ADD COLUMN IF NOT EXISTS paid_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS paid_working_days NUMERIC(9, 4),
    ADD COLUMN IF NOT EXISTS allowance_snapshot NUMERIC(15, 0),
    ADD COLUMN IF NOT EXISTS deduction_snapshot NUMERIC(15, 0),
    ADD COLUMN IF NOT EXISTS employee_code_snapshot VARCHAR(30),
    ADD COLUMN IF NOT EXISTS employee_full_name_snapshot VARCHAR(200),
    ADD COLUMN IF NOT EXISTS department_id_snapshot BIGINT,
    ADD COLUMN IF NOT EXISTS department_code_snapshot VARCHAR(30),
    ADD COLUMN IF NOT EXISTS department_name_snapshot VARCHAR(150),
    ADD CONSTRAINT ck_payroll_item_paid_minutes
        CHECK (paid_minutes IS NULL OR paid_minutes >= 0) NOT VALID,
    ADD CONSTRAINT ck_payroll_item_paid_working_days
        CHECK (paid_working_days IS NULL OR paid_working_days >= 0) NOT VALID,
    ADD CONSTRAINT ck_payroll_item_integral_money
        CHECK (
            base_salary_snapshot = trunc(base_salary_snapshot)
            AND earned_base_salary = trunc(earned_base_salary)
            AND allowance_amount = trunc(allowance_amount)
            AND deduction_amount = trunc(deduction_amount)
            AND net_salary = trunc(net_salary)
            AND (allowance_snapshot IS NULL OR allowance_snapshot = trunc(allowance_snapshot))
            AND (deduction_snapshot IS NULL OR deduction_snapshot = trunc(deduction_snapshot))
        ) NOT VALID,
    ADD CONSTRAINT ck_payroll_item_money_range
        CHECK (
            base_salary_snapshot BETWEEN 1 AND 999999999999
            AND earned_base_salary BETWEEN 0 AND 999999999999
            AND allowance_amount BETWEEN 0 AND 999999999999
            AND deduction_amount BETWEEN 0 AND 999999999999
            AND net_salary BETWEEN 0 AND 999999999999
            AND (allowance_snapshot IS NULL OR allowance_snapshot BETWEEN 0 AND 999999999999)
            AND (deduction_snapshot IS NULL OR deduction_snapshot BETWEEN 0 AND 999999999999)
        ) NOT VALID;

-- Q không được vượt C * M của kỳ. Tiền phải do service tính từ Q, C, M, không từ working_days làm tròn.
CREATE OR REPLACE FUNCTION validate_payroll_item_paid_minutes()
RETURNS TRIGGER AS $$
DECLARE
    run_standard_days INTEGER;
    run_standard_minutes INTEGER;
BEGIN
    IF NEW.paid_minutes IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT standard_working_days, standard_minutes_per_day
    INTO run_standard_days, run_standard_minutes
    FROM payroll_runs
    WHERE id = NEW.payroll_run_id;

    IF run_standard_days IS NULL OR run_standard_minutes IS NULL THEN
        RAISE EXCEPTION 'Payroll run % is missing C or M snapshot', NEW.payroll_run_id
            USING ERRCODE = '23514';
    END IF;

    IF NEW.paid_minutes > run_standard_days * run_standard_minutes THEN
        RAISE EXCEPTION 'Paid minutes % exceeds the payroll run limit %',
            NEW.paid_minutes, run_standard_days * run_standard_minutes
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payroll_item_validate_paid_minutes
BEFORE INSERT OR UPDATE OF payroll_run_id, paid_minutes
ON payroll_items
FOR EACH ROW EXECUTE FUNCTION validate_payroll_item_paid_minutes();

COMMENT ON COLUMN payroll_items.working_days IS
    'Legacy display cache only. Calculate payroll from paid_minutes and payroll_runs C/M snapshots.';
COMMENT ON COLUMN payroll_runs.revision IS
    'Legacy column retained for compatibility; payroll MVP only permits revision 1.';
