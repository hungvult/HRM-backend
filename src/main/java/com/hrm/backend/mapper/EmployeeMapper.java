package com.hrm.backend.mapper;

import com.hrm.backend.dto.request.UpdateEmployeeRequest;
import com.hrm.backend.entity.Employee;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EmployeeMapper {

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

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
