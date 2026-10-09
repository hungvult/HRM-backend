package com.hrm.backend.repository;

import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

@Repository
public interface EmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment, Long> {

    boolean existsByEmployeeIdAndManagerEmployeeIdAndIsCurrentTrue(Long employeeId, Long managerEmployeeId);

    @Query("""
            SELECT COUNT(assignment) FROM EmployeeAssignment assignment
            WHERE assignment.employee.id = :employeeId
              AND assignment.managerEmployee.id = :managerEmployeeId
              AND assignment.effectiveFrom <= :workDate
              AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= :workDate)
            """)
    long countManagedEmployeeAtDate(@Param("employeeId") Long employeeId,
                                    @Param("managerEmployeeId") Long managerEmployeeId,
                                    @Param("workDate") java.time.LocalDate workDate);

    boolean existsByDepartmentIdAndIsCurrentTrue(Long departmentId);

    boolean existsByPositionIdAndIsCurrentTrue(Long positionId);

    @Query("SELECT ea FROM EmployeeAssignment ea " +
           "LEFT JOIN FETCH ea.department " +
           "LEFT JOIN FETCH ea.position " +
           "LEFT JOIN FETCH ea.managerEmployee " +
           "WHERE ea.employee.id = :employeeId AND ea.isCurrent = true")
    Optional<EmployeeAssignment> findCurrentAssignmentByEmployeeId(@Param("employeeId") Long employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ea FROM EmployeeAssignment ea WHERE ea.employee.id = :employeeId AND ea.isCurrent = true")
    Optional<EmployeeAssignment> findCurrentAssignmentForUpdate(@Param("employeeId") Long employeeId);

    @EntityGraph(attributePaths = {"employee", "department", "position", "managerEmployee", "assignedByAccount"})
    Page<EmployeeAssignment> findByEmployeeIdOrderByEffectiveFromDescIdDesc(Long employeeId, Pageable pageable);
}
