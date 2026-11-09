package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollRun;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Kept separate from the write-side repository supplied by HRM-243. */
public interface PayrollRunViewRepository extends JpaRepository<PayrollRun, Long> {
    @Query("""
            select run from PayrollRun run
            where (:year is null or (run.payrollYear = :year and run.payrollMonth = :month))
              and (:status is null or run.status = :status)
              and (:fromYear is null or run.payrollYear > :fromYear
                   or (run.payrollYear = :fromYear and run.payrollMonth >= :fromMonth))
              and (:toYear is null or run.payrollYear < :toYear
                   or (run.payrollYear = :toYear and run.payrollMonth <= :toMonth))
            """)
    Page<PayrollRun> findPageByFilters(@Param("year") Short year,
                                       @Param("month") Short month,
                                       @Param("status") PayrollRunStatus status,
                                       @Param("fromYear") Short fromYear,
                                       @Param("fromMonth") Short fromMonth,
                                       @Param("toYear") Short toYear,
                                       @Param("toMonth") Short toMonth,
                                       Pageable pageable);
}
