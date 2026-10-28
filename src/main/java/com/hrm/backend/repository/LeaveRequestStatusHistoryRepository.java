package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveRequestStatusHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestStatusHistoryRepository extends JpaRepository<LeaveRequestStatusHistory, Long> {
    @EntityGraph(attributePaths = "changedByEmployee")
    List<LeaveRequestStatusHistory> findByLeaveRequestIdOrderByChangedAtAscIdAsc(Long leaveRequestId);
}
