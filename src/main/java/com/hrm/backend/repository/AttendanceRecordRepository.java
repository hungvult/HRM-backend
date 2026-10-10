package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import com.hrm.backend.entity.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "employee")
    @Query("""
            SELECT attendance FROM AttendanceRecord attendance
            WHERE attendance.employee.id = :employeeId
              AND attendance.workDate = :workDate
            """)
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDateForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("workDate") LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT attendance FROM AttendanceRecord attendance WHERE attendance.id = :attendanceId")
    Optional<AttendanceRecord> findByIdForUpdate(@Param("attendanceId") Long attendanceId);

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long employeeId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "employee")
    @Query("""
            SELECT attendance FROM AttendanceRecord attendance
            WHERE (:q = ''
                    OR LOWER(attendance.employee.employeeCode) LIKE LOWER(CONCAT('%', :q, '%'))
                    OR LOWER(attendance.employee.fullName) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:employeeId IS NULL OR attendance.employee.id = :employeeId)
              AND (:departmentId IS NULL OR EXISTS (
                    SELECT 1 FROM EmployeeAssignment assignment
                    WHERE assignment.employee = attendance.employee
                      AND assignment.department.id = :departmentId
                      AND assignment.effectiveFrom <= attendance.workDate
                      AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= attendance.workDate)))
              AND (:status IS NULL OR attendance.attendanceStatus = :status)
              AND (:fromDate IS NULL OR attendance.workDate >= :fromDate)
              AND (:toDate IS NULL OR attendance.workDate <= :toDate)
              AND (:managerEmployeeId IS NULL OR EXISTS (
                    SELECT 1 FROM EmployeeAssignment assignment
                    WHERE assignment.employee = attendance.employee
                      AND assignment.managerEmployee.id = :managerEmployeeId
                      AND assignment.effectiveFrom <= attendance.workDate
                      AND (assignment.effectiveTo IS NULL OR assignment.effectiveTo >= attendance.workDate)))
            """)
    Page<AttendanceRecord> searchAttendances(
            @Param("q") String q,
            @Param("employeeId") Long employeeId,
            @Param("departmentId") Long departmentId,
            @Param("status") AttendanceStatus status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("managerEmployeeId") Long managerEmployeeId,
            Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    @Query("SELECT attendance FROM AttendanceRecord attendance WHERE attendance.id = :attendanceId")
    Optional<AttendanceRecord> findDetailById(@Param("attendanceId") Long attendanceId);
}
