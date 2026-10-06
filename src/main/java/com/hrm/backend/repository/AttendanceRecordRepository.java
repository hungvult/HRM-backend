package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ar from AttendanceRecord ar
            where ar.employee.id = :employeeId and ar.workDate = :workDate
            """)
    Optional<AttendanceRecord> findByEmployeeIdAndWorkDateForUpdate(
            @Param("employeeId") Long employeeId,
            @Param("workDate") LocalDate workDate);

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    List<AttendanceRecord> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
            Long employeeId, LocalDate from, LocalDate to);
}
