package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import com.hrm.backend.entity.enums.AttendanceStatus;

@Entity
@Table(name = "attendance_adjustment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAdjustmentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @Column(name = "previous_check_in_at")
    private OffsetDateTime previousCheckInAt;

    @Column(name = "previous_check_out_at")
    private OffsetDateTime previousCheckOutAt;

    @Column(name = "previous_working_minutes")
    private Integer previousWorkingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_attendance_status", length = 30)
    private AttendanceStatus previousAttendanceStatus;

    @Column(name = "new_check_in_at")
    private OffsetDateTime newCheckInAt;

    @Column(name = "new_check_out_at")
    private OffsetDateTime newCheckOutAt;

    @Column(name = "new_working_minutes")
    private Integer newWorkingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_attendance_status", nullable = false, length = 30)
    private AttendanceStatus newAttendanceStatus;

    @Column(name = "adjustment_reason", nullable = false, columnDefinition = "TEXT")
    private String adjustmentReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_by_account_id", nullable = false)
    private Account adjustedByAccount;

    @Column(name = "adjusted_at", nullable = false, updatable = false)
    private OffsetDateTime adjustedAt;

    @PrePersist
    protected void onCreate() {
        if (adjustedAt == null) {
            adjustedAt = OffsetDateTime.now();
        }
    }
}
