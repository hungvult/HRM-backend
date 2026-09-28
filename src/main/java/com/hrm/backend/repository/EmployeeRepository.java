package com.hrm.backend.repository;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByAccountId(Long accountId);
    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT e FROM Employee e
            WHERE (:q = '' OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.email) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:employmentStatus IS NULL OR e.employmentStatus = :employmentStatus)
              AND (:departmentId IS NULL OR EXISTS (
                    SELECT 1 FROM EmployeeAssignment ea
                    WHERE ea.employee = e AND ea.isCurrent = true AND ea.department.id = :departmentId))
              AND (:positionId IS NULL OR EXISTS (
                    SELECT 1 FROM EmployeeAssignment ea
                    WHERE ea.employee = e AND ea.isCurrent = true AND ea.position.id = :positionId))
            """)
    Page<Employee> searchEmployees(
            @Param("q") String q,
            @Param("employmentStatus") EmploymentStatus employmentStatus,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            Pageable pageable);
}
