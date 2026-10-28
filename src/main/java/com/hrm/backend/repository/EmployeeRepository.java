package com.hrm.backend.repository;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.id = :employeeId")
    Optional<Employee> findByIdForUpdate(@Param("employeeId") Long employeeId);
    Optional<Employee> findByAccountId(Long accountId);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

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

    @Query("""
            SELECT e FROM Employee e
            WHERE (:q = '' OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(e.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:departmentId IS NULL OR EXISTS (
                    SELECT 1 FROM EmployeeAssignment ea
                    WHERE ea.employee = e AND ea.isCurrent = true AND ea.department.id = :departmentId))
              AND (
                    :profileState IS NULL
                    OR (:profileState = 'CURRENT' AND EXISTS (
                        SELECT 1 FROM EmployeeSalaryProfile profile
                        WHERE profile.employee = e
                          AND profile.effectiveFrom <= :today
                          AND (profile.effectiveTo IS NULL OR profile.effectiveTo >= :today)))
                    OR (:profileState = 'FUTURE'
                        AND NOT EXISTS (
                            SELECT 1 FROM EmployeeSalaryProfile profile
                            WHERE profile.employee = e
                              AND profile.effectiveFrom <= :today
                              AND (profile.effectiveTo IS NULL OR profile.effectiveTo >= :today))
                        AND EXISTS (
                            SELECT 1 FROM EmployeeSalaryProfile profile
                            WHERE profile.employee = e AND profile.effectiveFrom > :today))
                    OR (:profileState = 'HISTORICAL'
                        AND NOT EXISTS (
                            SELECT 1 FROM EmployeeSalaryProfile profile
                            WHERE profile.employee = e
                              AND profile.effectiveFrom <= :today
                              AND (profile.effectiveTo IS NULL OR profile.effectiveTo >= :today))
                        AND NOT EXISTS (
                            SELECT 1 FROM EmployeeSalaryProfile profile
                            WHERE profile.employee = e AND profile.effectiveFrom > :today)
                        AND EXISTS (
                            SELECT 1 FROM EmployeeSalaryProfile profile WHERE profile.employee = e))
                    OR (:profileState = 'NONE' AND NOT EXISTS (
                        SELECT 1 FROM EmployeeSalaryProfile profile WHERE profile.employee = e))
              )
            """)
    Page<Employee> searchPayrollEmployees(
            @Param("q") String q,
            @Param("departmentId") Long departmentId,
            @Param("profileState") String profileState,
            @Param("today") java.time.LocalDate today,
            Pageable pageable);
}
