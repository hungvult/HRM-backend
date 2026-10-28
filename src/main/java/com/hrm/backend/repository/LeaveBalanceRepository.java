package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveBalance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    @EntityGraph(attributePaths = "leaveType")
    List<LeaveBalance> findByEmployeeIdAndBalanceYearOrderByLeaveTypeCodeAsc(Long employeeId, Short balanceYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM LeaveBalance b JOIN FETCH b.leaveType
            WHERE b.employee.id = :employeeId AND b.leaveType.id = :leaveTypeId AND b.balanceYear = :year
            """)
    java.util.Optional<LeaveBalance> findForUpdate(@Param("employeeId") Long employeeId,
                                                    @Param("leaveTypeId") Long leaveTypeId,
                                                    @Param("year") Short year);
}
