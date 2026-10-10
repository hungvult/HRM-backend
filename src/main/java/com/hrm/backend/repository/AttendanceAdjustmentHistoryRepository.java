package com.hrm.backend.repository;

import com.hrm.backend.entity.AttendanceAdjustmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceAdjustmentHistoryRepository extends JpaRepository<AttendanceAdjustmentHistory, Long> {
    @EntityGraph(attributePaths = "adjustedByAccount")
    Page<AttendanceAdjustmentHistory> findByAttendanceRecordIdOrderByAdjustedAtDesc(Long attendanceRecordId, Pageable pageable);
}
