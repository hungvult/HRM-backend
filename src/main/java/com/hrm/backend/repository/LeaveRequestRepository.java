package com.hrm.backend.repository;

import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Collection;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("""
            SELECT r FROM LeaveRequest r WHERE r.employee.id = :employeeId
              AND (:status IS NULL OR r.status = :status)
              AND (:leaveTypeId IS NULL OR r.leaveType.id = :leaveTypeId)
              AND (CAST(:fromDate AS date) IS NULL OR r.endDate >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR r.startDate <= :toDate)
            """)
    Page<LeaveRequest> findPersonalRequests(@Param("employeeId") Long employeeId,
                                            @Param("status") LeaveRequestStatus status,
                                            @Param("leaveTypeId") Long leaveTypeId,
                                            @Param("fromDate") LocalDate fromDate,
                                            @Param("toDate") LocalDate toDate,
                                            Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("""
            SELECT r FROM LeaveRequest r WHERE
              (:companyWide = true OR r.employee.id = :viewerEmployeeId OR (:canReadDirectReports = true AND EXISTS (
                  SELECT 1 FROM EmployeeAssignment a
                  WHERE a.employee = r.employee AND a.managerEmployee.id = :viewerEmployeeId AND a.isCurrent = true)))
              AND (:status IS NULL OR r.status = :status)
              AND (:leaveTypeId IS NULL OR r.leaveType.id = :leaveTypeId)
              AND (CAST(:fromDate AS date) IS NULL OR r.endDate >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR r.startDate <= :toDate)
            """)
    Page<LeaveRequest> findRequestsInScope(@Param("companyWide") boolean companyWide,
                                           @Param("canReadDirectReports") boolean canReadDirectReports,
                                           @Param("viewerEmployeeId") Long viewerEmployeeId,
                                           @Param("status") LeaveRequestStatus status,
                                           @Param("leaveTypeId") Long leaveTypeId,
                                           @Param("fromDate") LocalDate fromDate,
                                           @Param("toDate") LocalDate toDate,
                                           Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    Optional<LeaveRequest> findWithEmployeeAndLeaveTypeById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("SELECT r FROM LeaveRequest r WHERE r.id = :id")
    Optional<LeaveRequest> findWithEmployeeAndLeaveTypeByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(r) > 0 FROM LeaveRequest r
            WHERE r.employee.id = :employeeId AND r.status IN :statuses
              AND r.startDate <= :endDate AND r.endDate >= :startDate
            """)
    boolean existsOverlappingRequest(@Param("employeeId") Long employeeId,
                                     @Param("statuses") Collection<LeaveRequestStatus> statuses,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(r.requestedDays), 0) FROM LeaveRequest r
            WHERE r.employee.id = :employeeId AND r.leaveType.id = :leaveTypeId
              AND r.status = :status AND r.startDate >= :yearStart AND r.endDate <= :yearEnd
            """)
    BigDecimal sumRequestedDays(@Param("employeeId") Long employeeId,
                                @Param("leaveTypeId") Long leaveTypeId,
                                @Param("status") LeaveRequestStatus status,
                                @Param("yearStart") LocalDate yearStart,
                                @Param("yearEnd") LocalDate yearEnd);
}
