package com.hrm.backend.dto.request;

import com.hrm.backend.entity.enums.EmployeeGender;
import com.hrm.backend.entity.enums.EmploymentStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateEmployeeRequest {
    @NotBlank @Size(max = 200)
    private String fullName;

    @Past
    private LocalDate dateOfBirth;

    private EmployeeGender gender;

    @NotBlank @Email @Size(max = 255)
    private String email;

    @NotBlank @Size(max = 30)
    private String phone;

    @Size(max = 2000)
    private String address;

    @NotNull
    private LocalDate hireDate;

    private EmploymentStatus employmentStatus = EmploymentStatus.WORKING;
}
