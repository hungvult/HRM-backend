package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface LeaveRequestAttachmentRepository extends JpaRepository<LeaveRequestAttachment, Long> {
    // Chi tiết đơn chỉ tải metadata, không kéo bytea 5 MB vào bộ nhớ hay nhúng vào JSON.
    @Query("select a.fileName as fileName, a.contentType as contentType, a.sizeBytes as sizeBytes from LeaveRequestAttachment a where a.leaveRequestId = :id")
    Optional<Metadata> findMetadataByLeaveRequestId(Long id);

    interface Metadata {
        String getFileName();
        String getContentType();
        Long getSizeBytes();
    }
}
