package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

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
}
