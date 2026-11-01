-- Xóa mềm danh mục để giữ FK của lịch sử phân công.
ALTER TABLE departments ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE positions ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_employee_code_ci ON employees (upper(employee_code));
CREATE UNIQUE INDEX uq_department_code_ci ON departments (upper(code));
CREATE UNIQUE INDEX uq_position_code_ci ON positions (upper(code));

-- Sửa khoảng ngày sai do phân công nhiều lần cùng ngày; giữ nguyên từng bản và người tạo.
INSERT INTO audit_logs (action, entity_type, entity_id, old_data, new_data, occurred_at)
 SELECT 'ASSIGNMENT_DATE_REPAIRED', 'EMPLOYEE_ASSIGNMENT', id,
 jsonb_build_object('effectiveTo', effective_to), jsonb_build_object('effectiveTo', effective_from), now()
 FROM employee_assignments WHERE effective_to < effective_from;
UPDATE employee_assignments SET effective_to=effective_from WHERE effective_to < effective_from;
ALTER TABLE employee_assignments ADD CONSTRAINT ck_assignment_date_order
 CHECK (effective_to IS NULL OR effective_to >= effective_from) NOT VALID;
