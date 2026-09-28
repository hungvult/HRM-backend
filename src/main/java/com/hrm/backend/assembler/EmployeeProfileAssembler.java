package com.hrm.backend.assembler;

import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmployeeProfileAssembler {
    private final EmployeeAssignmentRepository assignments;

    public EmployeeDto toDetailedDto(Employee employee) {
        EmployeeDto.EmployeeDtoBuilder builder = EmployeeDto.builder()
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
        assignments.findCurrentAssignmentByEmployeeId(employee.getId()).ifPresent(assignment -> apply(builder, assignment));
        return builder.build();
    }

    private void apply(EmployeeDto.EmployeeDtoBuilder builder, EmployeeAssignment assignment) {
        if (assignment.getDepartment() != null) {
            builder.department(DepartmentDto.builder().id(assignment.getDepartment().getId())
                    .code(assignment.getDepartment().getCode()).name(assignment.getDepartment().getName()).build());
        }
        if (assignment.getPosition() != null) {
            builder.position(PositionDto.builder().id(assignment.getPosition().getId())
                    .code(assignment.getPosition().getCode()).name(assignment.getPosition().getName()).build());
        }
        if (assignment.getManagerEmployee() != null) {
            builder.manager(EmployeeDto.ManagerDto.builder().id(assignment.getManagerEmployee().getId())
                    .employeeCode(assignment.getManagerEmployee().getEmployeeCode())
                    .fullName(assignment.getManagerEmployee().getFullName()).build());
        }
    }

}
