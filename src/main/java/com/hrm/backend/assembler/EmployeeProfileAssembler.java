package com.hrm.backend.assembler;

import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.EmployeeSummaryDto;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.mapper.EmployeeMapper;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EmployeeProfileAssembler {
    private final EmployeeMapper employeeMapper;
    private final EmployeeAssignmentRepository assignments;

    public EmployeeDto toDetailedDto(Employee employee) {
        EmployeeDto.EmployeeDtoBuilder builder = copy(employeeMapper.toDto(employee));
        assignments.findCurrentAssignmentByEmployeeId(employee.getId()).ifPresent(assignment -> apply(builder, assignment));
        return builder.build();
    }

    public List<EmployeeSummaryDto> toSummaryDtos(Collection<Employee> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeAssignment> assignmentsByEmployeeId = assignments
                .findCurrentAssignmentsByEmployeeIdIn(employees.stream().map(Employee::getId).toList())
                .stream()
                .collect(Collectors.toMap(assignment -> assignment.getEmployee().getId(), Function.identity()));

        return employees.stream().map(employee -> {
            EmployeeSummaryDto.EmployeeSummaryDtoBuilder builder = copy(employeeMapper.toSummaryDto(employee));
            EmployeeAssignment assignment = assignmentsByEmployeeId.get(employee.getId());
            if (assignment != null) {
                apply(builder, assignment);
            }
            return builder.build();
        }).toList();
    }

    private EmployeeDto.EmployeeDtoBuilder copy(EmployeeDto source) {
        return EmployeeDto.builder().id(source.getId()).employeeCode(source.getEmployeeCode())
                .fullName(source.getFullName()).dateOfBirth(source.getDateOfBirth()).gender(source.getGender())
                .email(source.getEmail()).phone(source.getPhone()).address(source.getAddress())
                .hireDate(source.getHireDate()).employmentStatus(source.getEmploymentStatus());
    }

    private EmployeeSummaryDto.EmployeeSummaryDtoBuilder copy(EmployeeSummaryDto source) {
        return EmployeeSummaryDto.builder().id(source.getId()).employeeCode(source.getEmployeeCode())
                .fullName(source.getFullName()).email(source.getEmail()).phone(source.getPhone())
                .hireDate(source.getHireDate()).employmentStatus(source.getEmploymentStatus());
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

    private void apply(EmployeeSummaryDto.EmployeeSummaryDtoBuilder builder, EmployeeAssignment assignment) {
        if (assignment.getDepartment() != null) {
            builder.department(DepartmentDto.builder().id(assignment.getDepartment().getId())
                    .code(assignment.getDepartment().getCode()).name(assignment.getDepartment().getName()).build());
        }
        if (assignment.getPosition() != null) {
            builder.position(PositionDto.builder().id(assignment.getPosition().getId())
                    .code(assignment.getPosition().getCode()).name(assignment.getPosition().getName()).build());
        }
    }
}
