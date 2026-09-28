package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.EmployeeGender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** All fields are optional; only supplied fields are changed. */
@Data
public class UpdateEmployeeRequest {
    @Size(min = 1, max = 200)
    private String fullName;

    @Past
    private LocalDate dateOfBirth;

    private EmployeeGender gender;

    @Email
    @Size(max = 255)
    private String email;

    @Size(min = 1, max = 30)
    private String phone;

    @Size(max = 2000)
    private String address;

    private LocalDate hireDate;

    public boolean hasChanges() {
        return fullName != null || dateOfBirth != null || gender != null || email != null
                || phone != null || address != null || hireDate != null;
    }
}
