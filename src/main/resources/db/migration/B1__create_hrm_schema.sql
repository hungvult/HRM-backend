-- HRM / PostgreSQL / Flyway
-- Quy ước: timestamptz được lưu ở UTC; các mã nghiệp vụ dùng cột *_code.

CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    is_system BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_roles_code CHECK (code = upper(code))
);

CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE UNIQUE INDEX uq_accounts_username_ci ON accounts (lower(username));
CREATE UNIQUE INDEX uq_accounts_email_ci ON accounts (lower(email));

CREATE TABLE account_roles (
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    PRIMARY KEY (account_id, role_id)
);

CREATE TABLE departments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_departments_code CHECK (code = upper(code))
);
CREATE UNIQUE INDEX uq_departments_active_name ON departments (lower(name)) WHERE status = 'ACTIVE';

CREATE TABLE positions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_positions_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_positions_code CHECK (code = upper(code))
);
CREATE UNIQUE INDEX uq_positions_active_name ON positions (lower(name)) WHERE status = 'ACTIVE';

CREATE TABLE employees (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    account_id BIGINT UNIQUE REFERENCES accounts(id) ON DELETE SET NULL,
    full_name VARCHAR(200) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    address TEXT,
    hire_date DATE NOT NULL,
    employment_status VARCHAR(20) NOT NULL DEFAULT 'WORKING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_employees_status CHECK (employment_status IN ('WORKING', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT ck_employees_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_employees_code CHECK (employee_code = upper(employee_code)),
    CONSTRAINT ck_employees_birth_before_hire CHECK (date_of_birth IS NULL OR hire_date >= date_of_birth)
);
CREATE UNIQUE INDEX uq_employees_email_ci ON employees (lower(email));
CREATE INDEX ix_employees_status ON employees (employment_status);

CREATE TABLE employee_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    reason TEXT,
    CONSTRAINT ck_employee_status_history_old CHECK (old_status IS NULL OR old_status IN ('WORKING', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT ck_employee_status_history_new CHECK (new_status IN ('WORKING', 'ON_LEAVE', 'TERMINATED'))
);

-- Một dòng is_current = true là phân công hiện hành. Những dòng cũ giữ lịch sử.
CREATE TABLE employee_assignments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    department_id BIGINT REFERENCES departments(id) ON DELETE RESTRICT,
    position_id BIGINT REFERENCES positions(id) ON DELETE RESTRICT,
    manager_employee_id BIGINT REFERENCES employees(id) ON DELETE RESTRICT,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_assignments_date_range CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_assignments_not_self_managed CHECK (manager_employee_id IS NULL OR manager_employee_id <> employee_id),
    CONSTRAINT ck_assignments_current_end CHECK ((is_current AND effective_to IS NULL) OR (NOT is_current AND effective_to IS NOT NULL))
);
CREATE UNIQUE INDEX uq_employee_current_assignment ON employee_assignments (employee_id) WHERE is_current;
CREATE INDEX ix_assignments_manager_current ON employee_assignments (manager_employee_id) WHERE is_current;
CREATE INDEX ix_assignments_department_current ON employee_assignments (department_id) WHERE is_current;

CREATE TABLE employment_contracts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    contract_code VARCHAR(50) NOT NULL UNIQUE,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    contract_type VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    signed_date DATE,
    base_salary NUMERIC(14,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    document_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_contract_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_contract_salary CHECK (base_salary IS NULL OR base_salary > 0),
    CONSTRAINT ck_contract_status CHECK (status IN ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED'))
);
CREATE INDEX ix_contracts_employee_dates ON employment_contracts (employee_id, start_date DESC);
CREATE INDEX ix_contracts_active_end_date ON employment_contracts (end_date) WHERE status = 'ACTIVE' AND end_date IS NOT NULL;

CREATE TABLE attendance_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    work_date DATE NOT NULL,
    check_in_at TIMESTAMPTZ,
    check_out_at TIMESTAMPTZ,
    check_in_latitude NUMERIC(9,6),
    check_in_longitude NUMERIC(9,6),
    check_out_latitude NUMERIC(9,6),
    check_out_longitude NUMERIC(9,6),
    check_in_wifi_ssid VARCHAR(255),
    check_out_wifi_ssid VARCHAR(255),
    working_minutes INTEGER,
    attendance_status VARCHAR(30) NOT NULL DEFAULT 'PRESENT',
    is_manually_adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    adjustment_reason TEXT,
    adjusted_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attendance_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT ck_attendance_time_order CHECK (check_out_at IS NULL OR check_in_at IS NULL OR check_out_at >= check_in_at),
    CONSTRAINT ck_attendance_working_minutes CHECK (working_minutes IS NULL OR working_minutes >= 0),
    CONSTRAINT ck_attendance_status CHECK (attendance_status IN ('PRESENT', 'LATE', 'INCOMPLETE', 'ABSENT', 'LEAVE', 'HOLIDAY')),
    CONSTRAINT ck_check_in_latitude CHECK (check_in_latitude IS NULL OR check_in_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_check_in_longitude CHECK (check_in_longitude IS NULL OR check_in_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_check_out_latitude CHECK (check_out_latitude IS NULL OR check_out_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_check_out_longitude CHECK (check_out_longitude IS NULL OR check_out_longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_attendance_date_employee ON attendance_records (work_date DESC, employee_id);

CREATE TABLE leave_types (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    annual_quota_days NUMERIC(5,2) NOT NULL DEFAULT 0,
    is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_leave_types_quota CHECK (annual_quota_days >= 0),
    CONSTRAINT ck_leave_types_code CHECK (code = upper(code))
);

CREATE TABLE leave_balances (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    leave_type_id BIGINT NOT NULL REFERENCES leave_types(id) ON DELETE RESTRICT,
    balance_year SMALLINT NOT NULL,
    allocated_days NUMERIC(5,2) NOT NULL DEFAULT 0,
    used_days NUMERIC(5,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_leave_balance UNIQUE (employee_id, leave_type_id, balance_year),
    CONSTRAINT ck_leave_balance_year CHECK (balance_year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_leave_balance_values CHECK (allocated_days >= 0 AND used_days >= 0 AND used_days <= allocated_days)
);

CREATE TABLE leave_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_code VARCHAR(50) NOT NULL UNIQUE,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    leave_type_id BIGINT NOT NULL REFERENCES leave_types(id) ON DELETE RESTRICT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    requested_days NUMERIC(5,2) NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by_employee_id BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_leave_request_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_leave_request_days CHECK (requested_days > 0),
    CONSTRAINT ck_leave_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_leave_request_reviewer CHECK ((status IN ('APPROVED', 'REJECTED') AND reviewed_by_employee_id IS NOT NULL AND reviewed_at IS NOT NULL) OR status IN ('PENDING', 'CANCELLED')),
    CONSTRAINT ck_leave_request_rejection_reason CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
CREATE INDEX ix_leave_requests_reviewer_pending ON leave_requests (reviewed_by_employee_id) WHERE status = 'PENDING';
CREATE INDEX ix_leave_requests_employee_date ON leave_requests (employee_id, start_date DESC);

CREATE TABLE leave_request_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    leave_request_id BIGINT NOT NULL REFERENCES leave_requests(id) ON DELETE RESTRICT,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by_employee_id BIGINT REFERENCES employees(id) ON DELETE SET NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note TEXT,
    CONSTRAINT ck_leave_history_status CHECK (new_status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE employee_salary_profiles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    effective_from DATE NOT NULL,
    effective_to DATE,
    base_salary NUMERIC(14,2) NOT NULL,
    fixed_allowance NUMERIC(14,2) NOT NULL DEFAULT 0,
    fixed_deduction NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_salary_profile_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_salary_profile_base CHECK (base_salary > 0),
    CONSTRAINT ck_salary_profile_amounts CHECK (fixed_allowance >= 0 AND fixed_deduction >= 0)
);
CREATE UNIQUE INDEX uq_employee_current_salary_profile ON employee_salary_profiles (employee_id) WHERE effective_to IS NULL;
CREATE INDEX ix_salary_profiles_effective ON employee_salary_profiles (employee_id, effective_from DESC);

CREATE TABLE payroll_runs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_year SMALLINT NOT NULL,
    payroll_month SMALLINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    generated_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    generated_at TIMESTAMPTZ,
    published_by_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_period UNIQUE (payroll_year, payroll_month),
    CONSTRAINT ck_payroll_period CHECK (payroll_year BETWEEN 2000 AND 2100 AND payroll_month BETWEEN 1 AND 12),
    CONSTRAINT ck_payroll_run_status CHECK (status IN ('DRAFT', 'GENERATED', 'PUBLISHED', 'CANCELLED'))
);

CREATE TABLE payroll_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_run_id BIGINT NOT NULL REFERENCES payroll_runs(id) ON DELETE RESTRICT,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE RESTRICT,
    salary_profile_id BIGINT REFERENCES employee_salary_profiles(id) ON DELETE SET NULL,
    working_days NUMERIC(5,2) NOT NULL DEFAULT 0,
    standard_working_days NUMERIC(5,2) NOT NULL,
    base_salary_snapshot NUMERIC(14,2) NOT NULL,
    earned_base_salary NUMERIC(14,2) NOT NULL,
    allowance_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_salary NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_item_employee UNIQUE (payroll_run_id, employee_id),
    CONSTRAINT ck_payroll_item_days CHECK (working_days >= 0 AND standard_working_days > 0),
    CONSTRAINT ck_payroll_item_amounts CHECK (base_salary_snapshot > 0 AND earned_base_salary >= 0 AND allowance_amount >= 0 AND deduction_amount >= 0)
);
CREATE INDEX ix_payroll_items_employee ON payroll_items (employee_id, payroll_run_id);

CREATE TABLE notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    related_entity_type VARCHAR(50),
    related_entity_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_read_at CHECK ((is_read AND read_at IS NOT NULL) OR NOT is_read)
);
CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_account_id, created_at DESC) WHERE NOT is_read;
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_account_id, created_at DESC);

-- Nhật ký truy vết cho các thao tác Admin/HR/Manager ngoài các lịch sử nghiệp vụ ở trên.
CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_account_id BIGINT REFERENCES accounts(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    old_data JSONB,
    new_data JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address INET
);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_accounts_updated_at BEFORE UPDATE ON accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_departments_updated_at BEFORE UPDATE ON departments FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_positions_updated_at BEFORE UPDATE ON positions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_employees_updated_at BEFORE UPDATE ON employees FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_contracts_updated_at BEFORE UPDATE ON employment_contracts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_attendance_updated_at BEFORE UPDATE ON attendance_records FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_leave_balances_updated_at BEFORE UPDATE ON leave_balances FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_leave_requests_updated_at BEFORE UPDATE ON leave_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_salary_profiles_updated_at BEFORE UPDATE ON employee_salary_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payroll_runs_updated_at BEFORE UPDATE ON payroll_runs FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payroll_items_updated_at BEFORE UPDATE ON payroll_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();

