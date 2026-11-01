package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_request_attachments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveRequestAttachment {
    // Khóa chính cũng là FK leave_requests.id: mỗi đơn có tối đa một ảnh, không có URL công khai.
    @Id
    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id")
    private LeaveRequest leaveRequest;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;
}
