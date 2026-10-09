package com.hrm.backend.dto.response;

import com.hrm.backend.entity.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class AttendanceDetailResponse {
    private Long id;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private DepartmentDto department;
    private LocalDate workDate;
    private OffsetDateTime checkInAt;
    private OffsetDateTime checkOutAt;
    private BigDecimal checkInLatitude;
    private BigDecimal checkInLongitude;
    private BigDecimal checkOutLatitude;
    private BigDecimal checkOutLongitude;
    private String checkInWifiSsid;
    private String checkOutWifiSsid;
    private Integer workingMinutes;
    private AttendanceStatus attendanceStatus;
    private Boolean manuallyAdjusted;
    private String adjustmentReason;
    private Long adjustedByAccountId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
