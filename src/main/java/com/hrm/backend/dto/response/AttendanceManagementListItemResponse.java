package com.hrm.backend.dto.response;

import com.hrm.backend.entity.enums.AttendanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Response model for HR and manager attendance lists. */
@Data
@Builder
public class AttendanceManagementListItemResponse {
    private Long id;
    private Long employeeId;
    private String employeeCode;
    private String employeeName;
    private DepartmentDto department;
    private LocalDate workDate;
    private OffsetDateTime checkInAt;
    private OffsetDateTime checkOutAt;
    private Integer workingMinutes;
    private AttendanceStatus attendanceStatus;
    private Boolean manuallyAdjusted;
}
