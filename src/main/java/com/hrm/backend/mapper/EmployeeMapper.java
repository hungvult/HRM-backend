package com.hrm.backend.mapper;

import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.stereotype.Component;

/** Maps an employee profile and its current assignment to the detail response. */
@Component
public class EmployeeMapper {

    public EmployeeDto toDetailResponse(Employee employee, EmployeeAssignment assignment) {
        EmployeeDto.EmployeeDtoBuilder response = EmployeeDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender() == null ? null : employee.getGender().name())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .address(employee.getAddress())
                .hireDate(employee.getHireDate())
                .employmentStatus(employee.getEmploymentStatus() == null ? null : employee.getEmploymentStatus().name());

        if (assignment == null) {
            return response.build();
        }
        if (assignment.getDepartment() != null) {
            response.department(DepartmentDto.builder()
                    .id(assignment.getDepartment().getId())
                    .code(assignment.getDepartment().getCode())
                    .name(assignment.getDepartment().getName())
                    .build());
        }
        if (assignment.getPosition() != null) {
            response.position(PositionDto.builder()
                    .id(assignment.getPosition().getId())
                    .code(assignment.getPosition().getCode())
                    .name(assignment.getPosition().getName())
                    .build());
        }
        if (assignment.getManagerEmployee() != null) {
            response.manager(EmployeeDto.ManagerDto.builder()
                    .id(assignment.getManagerEmployee().getId())
                    .employeeCode(assignment.getManagerEmployee().getEmployeeCode())
                    .fullName(assignment.getManagerEmployee().getFullName())
                    .build());
        }
        return response.build();
    }
}
