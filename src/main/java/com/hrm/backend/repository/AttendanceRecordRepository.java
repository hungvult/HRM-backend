package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long>, JpaSpecificationExecutor<AttendanceRecord> {

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
    List<AttendanceRecord> findByEmployeeIdInAndWorkDateBetween(
            List<Long> employeeIds, LocalDate from, LocalDate to);

    @Override
    @EntityGraph(attributePaths = "employee")
    Page<AttendanceRecord> findAll(org.springframework.data.jpa.domain.Specification<AttendanceRecord> specification,
                                   Pageable pageable);

    @EntityGraph(attributePaths = "employee")
    @Query("SELECT attendance FROM AttendanceRecord attendance WHERE attendance.id = :attendanceId")
    Optional<AttendanceRecord> findDetailById(@Param("attendanceId") Long attendanceId);
}
