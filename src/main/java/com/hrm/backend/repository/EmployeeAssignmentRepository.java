package com.hrm.backend.repository;

import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    boolean existsByEmployeeIdAndManagerEmployeeIdAndIsCurrentTrue(Long employeeId, Long managerEmployeeId);

    @Query("SELECT ea FROM EmployeeAssignment ea " +
           "LEFT JOIN FETCH ea.department " +
           "LEFT JOIN FETCH ea.position " +
           "LEFT JOIN FETCH ea.managerEmployee " +
           "WHERE ea.employee.id = :employeeId AND ea.isCurrent = true")
    Optional<EmployeeAssignment> findCurrentAssignmentByEmployeeId(@Param("employeeId") Long employeeId);
}
