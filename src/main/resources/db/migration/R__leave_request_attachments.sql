-- Chỉ thêm bảng ảnh liên quan; không đòi hỏi V4/V5, không thay đổi đơn/quỹ/lịch sử hiện có.
-- PK cũng là FK: mỗi đơn tối đa một ảnh; bytea cùng transaction nên rollback không để lại file mồ côi.
DO $$
BEGIN
    IF to_regclass('public.leave_requests') IS NULL THEN RETURN; END IF;
    CREATE TABLE IF NOT EXISTS public.leave_request_attachments (
        leave_request_id BIGINT PRIMARY KEY REFERENCES public.leave_requests(id) ON DELETE CASCADE,
        file_name VARCHAR(255) NOT NULL,
        content_type VARCHAR(30) NOT NULL,
        size_bytes BIGINT NOT NULL,
        data BYTEA NOT NULL,
        CONSTRAINT ck_leave_attachment_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
        CONSTRAINT ck_leave_attachment_size CHECK (size_bytes > 0 AND size_bytes <= 5242880 AND size_bytes = octet_length(data)),
        CONSTRAINT ck_leave_attachment_name CHECK (NULLIF(btrim(file_name), '') IS NOT NULL)
    );
END $$;
