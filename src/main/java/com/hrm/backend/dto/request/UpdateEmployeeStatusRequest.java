package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.EmploymentStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateEmployeeStatusRequest {
    @NotNull
    private EmploymentStatus employmentStatus;

    @Size(max = 2000)
    private String reason;

    @AssertTrue(message = "Lý do là bắt buộc khi chuyển trạng thái sang TERMINATED.")
    public boolean isReasonValidForStatus() {
        return employmentStatus != EmploymentStatus.TERMINATED || (reason != null && !reason.isBlank());
    }
}
