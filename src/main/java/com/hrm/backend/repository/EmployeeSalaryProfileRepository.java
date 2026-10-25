package com.hrm.backend.repository;

import com.hrm.backend.entity.EmployeeSalaryProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeSalaryProfileRepository extends JpaRepository<EmployeeSalaryProfile, Long> {
    List<EmployeeSalaryProfile> findByEmployeeIdOrderByEffectiveFromDescIdDesc(Long employeeId);
}
