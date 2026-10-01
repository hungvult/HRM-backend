package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.hrm.backend.entity.enums.AttendanceStatus;

@Entity
@Table(name = "attendance_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at")
    private OffsetDateTime checkInAt;

    @Column(name = "check_out_at")
    private OffsetDateTime checkOutAt;

    @Column(name = "check_in_latitude", precision = 9, scale = 6)
    private BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 9, scale = 6)
    private BigDecimal checkInLongitude;

    @Column(name = "check_out_latitude", precision = 9, scale = 6)
    private BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 9, scale = 6)
    private BigDecimal checkOutLongitude;

    @Column(name = "check_in_wifi_ssid", length = 255)
    private String checkInWifiSsid;

    @Column(name = "check_out_wifi_ssid", length = 255)
    private String checkOutWifiSsid;

    @Column(name = "working_minutes")
    private Integer workingMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 30)
    private AttendanceStatus attendanceStatus = AttendanceStatus.PRESENT;

    @Column(name = "is_manually_adjusted", nullable = false)
    private Boolean isManuallyAdjusted = false;

    @Column(name = "adjustment_reason", columnDefinition = "TEXT")
    private String adjustmentReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adjusted_by_account_id")
    private Account adjustedByAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_work_location_id")
    private WorkLocation checkInWorkLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_out_work_location_id")
    private WorkLocation checkOutWorkLocation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
