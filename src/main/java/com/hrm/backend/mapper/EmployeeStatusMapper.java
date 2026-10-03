package com.hrm.backend.mapper;

import com.hrm.backend.dto.response.UpdateEmployeeStatusResponse;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeStatusHistory;
import org.springframework.stereotype.Component;

@Component
public class EmployeeStatusMapper {

    public UpdateEmployeeStatusResponse toResponse(Employee employee, EmployeeStatusHistory history) {
        return UpdateEmployeeStatusResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .employmentStatus(employee.getEmploymentStatus().name())
                .statusChangedAt(history.getChangedAt())
                .statusChangedByAccountId(history.getChangedByAccount().getId())
                .reason(history.getReason())
                .build();
    }
}
