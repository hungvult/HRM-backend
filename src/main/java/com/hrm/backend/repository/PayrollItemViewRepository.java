package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollItem;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/** Read model for immutable payroll-item snapshots. */
public interface PayrollItemViewRepository extends JpaRepository<PayrollItem, Long> {

    @Query("""
            select item from PayrollItem item
            where item.payrollRun.id = :runId
              and (:q is null
                   or lower(item.employeeCodeSnapshot) like lower(concat('%', :q, '%'))
                   or lower(item.employeeFullNameSnapshot) like lower(concat('%', :q, '%')))
              and (:departmentId is null or item.departmentIdSnapshot = :departmentId)
            """)
    Page<PayrollItem> findPageByRunSnapshotFilters(@Param("runId") Long runId,
                                                    @Param("q") String q,
                                                    @Param("departmentId") Long departmentId,
                                                    Pageable pageable);

    @Query("""
            select count(item) as employeeCount,
                   coalesce(sum(item.earnedBaseSalary), 0) as totalEarnedBaseSalary,
                   coalesce(sum(item.allowanceAmount), 0) as totalEarnedAllowance,
                   coalesce(sum(item.deductionAmount), 0) as totalEarnedDeduction,
                   coalesce(sum(item.netSalary), 0) as totalNetSalary
            from PayrollItem item
            where item.payrollRun.id = :runId
              and (:q is null
                   or lower(item.employeeCodeSnapshot) like lower(concat('%', :q, '%'))
                   or lower(item.employeeFullNameSnapshot) like lower(concat('%', :q, '%')))
              and (:departmentId is null or item.departmentIdSnapshot = :departmentId)
            """)
    Totals summarizeByRunSnapshotFilters(@Param("runId") Long runId,
                                          @Param("q") String q,
                                          @Param("departmentId") Long departmentId);

    default Totals summarizeByRunId(Long runId) {
        return summarizeByRunSnapshotFilters(runId, null, null);
    }

    @Query("""
            select item from PayrollItem item
            where item.employee.id = :employeeId
              and item.payrollRun.status = :status
              and (:year is null or item.payrollRun.payrollYear = :year)
            """)
    Page<PayrollItem> findPublishedByEmployee(@Param("employeeId") Long employeeId,
                                               @Param("status") PayrollRunStatus status,
                                               @Param("year") Short year,
                                               Pageable pageable);

    @Query("""
            select item from PayrollItem item
            where item.payrollRun.id = :runId
              and item.employee.id = :employeeId
              and item.payrollRun.status = :status
            """)
    Optional<PayrollItem> findPublishedByRunAndEmployee(@Param("runId") Long runId,
                                                         @Param("employeeId") Long employeeId,
                                                         @Param("status") PayrollRunStatus status);

    interface Totals {
        long getEmployeeCount();
        BigDecimal getTotalEarnedBaseSalary();
        BigDecimal getTotalEarnedAllowance();
        BigDecimal getTotalEarnedDeduction();
        BigDecimal getTotalNetSalary();
    }
}
