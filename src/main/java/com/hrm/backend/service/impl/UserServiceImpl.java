package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.*;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final EmployeeAssignmentRepository employeeAssignmentRepository;
    @Override
    public CurrentUserResponse getCurrentUserProfile(Long accountId) {
        Account a = accountRepository.findByIdWithEmployee(accountId).orElseThrow(() -> new IllegalArgumentException("Account does not exist."));
        List<String> roles = accountRoleRepository.findByAccountIdWithRole(a.getId())
                .stream()
                .map(AccountRole::getRole)
                .map(Role::getCode)
                .toList();
        return CurrentUserResponse.builder()
                .id(a.getId())
                .username(a.getUsername())
                .email(a.getEmail())
                .roles(roles)
                .employee(a.getEmployee() == null ? null : map(a.getEmployee())).build();
    }
    private EmployeeDto map(Employee e) {
        EmployeeDto.EmployeeDtoBuilder out = EmployeeDto.builder()
                .id(e.getId())
                .employeeCode(e.getEmployeeCode())
                .fullName(e.getFullName()).dateOfBirth(e.getDateOfBirth()).gender(e.getGender() == null ? null : e.getGender().name()).email(e.getEmail()).phone(e.getPhone()).address(e.getAddress()).hireDate(e.getHireDate()).employmentStatus(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name());
        employeeAssignmentRepository.findCurrentAssignmentByEmployeeId(e.getId()).ifPresent(a -> { if (a.getDepartment() != null) out.department(DepartmentDto.builder().id(a.getDepartment().getId()).code(a.getDepartment().getCode()).name(a.getDepartment().getName()).build()); if (a.getPosition() != null) out.position(PositionDto.builder().id(a.getPosition().getId()).code(a.getPosition().getCode()).name(a.getPosition().getName()).build()); if (a.getManagerEmployee() != null) out.manager(EmployeeDto.ManagerDto.builder().id(a.getManagerEmployee().getId()).employeeCode(a.getManagerEmployee().getEmployeeCode()).fullName(a.getManagerEmployee().getFullName()).build()); });
        return out.build();
    }
}
