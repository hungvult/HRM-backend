package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
public class CreateAttendanceRequest {
    @NotNull
    private Long employeeId;
    @NotNull
    private LocalDate workDate;
    private OffsetDateTime checkInAt;
    private OffsetDateTime checkOutAt;
    @NotNull
    private AttendanceStatus attendanceStatus;
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
