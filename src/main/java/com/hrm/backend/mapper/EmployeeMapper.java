package com.hrm.backend.mapper;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.request.UpdateEmployeeRequest;
import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmployeeMapper {

    public Employee toNewEntity(CreateEmployeeRequest request) {
        return Employee.builder()
                .fullName(request.getFullName().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .email(normalizeEmail(request.getEmail()))
                .phone(request.getPhone().trim())
                .address(normalizeNullable(request.getAddress()))
                .hireDate(request.getHireDate())
                .build();
    }

    public EmployeeDto toDto(Employee employee) {
        return EmployeeDto.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender() == null ? null : employee.getGender().name())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .address(employee.getAddress())
                .hireDate(employee.getHireDate())
                .employmentStatus(employee.getEmploymentStatus() == null
                        ? null
                        : employee.getEmploymentStatus().name())
                .build();
    }

    /** Updates only fields supplied by the client. Generated and assignment fields are untouched. */
    public void updateEntity(UpdateEmployeeRequest request, Employee employee) {
        if (request.getFullName() != null) employee.setFullName(request.getFullName().trim());
        if (request.getDateOfBirth() != null) employee.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) employee.setGender(request.getGender());
        if (request.getEmail() != null) employee.setEmail(normalizeEmail(request.getEmail()));
        if (request.getPhone() != null) employee.setPhone(request.getPhone().trim());
        if (request.getAddress() != null) employee.setAddress(request.getAddress().trim());
        if (request.getHireDate() != null) employee.setHireDate(request.getHireDate());
    }

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
                .employmentStatus(employee.getEmploymentStatus() == null
                        ? null
                        : employee.getEmploymentStatus().name());

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

    public String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }
}
