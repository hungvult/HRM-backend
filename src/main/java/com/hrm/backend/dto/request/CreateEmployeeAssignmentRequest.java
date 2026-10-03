package com.hrm.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateEmployeeAssignmentRequest {
    @NotNull(message = "employeeId là bắt buộc.")
    private Long employeeId;

    @NotNull(message = "departmentId là bắt buộc.")
    private Long departmentId;

    @NotNull(message = "positionId là bắt buộc.")
    private Long positionId;

    private Long managerEmployeeId;

    /** Nếu không truyền, hệ thống lấy ngày hiện tại. API này chưa hỗ trợ phân công theo lịch. */
    private LocalDate effectiveFrom;
}
