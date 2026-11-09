package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {
    boolean existsBySalaryProfileId(Long salaryProfileId);

    Optional<PayrollItem> findFirstBySalaryProfileIdOrderByPayrollRunPayrollYearDescPayrollRunPayrollMonthDesc(
            Long salaryProfileId);

    Optional<PayrollItem> findByPayrollRunIdAndEmployeeId(Long payrollRunId, Long employeeId);

    java.util.List<PayrollItem> findByPayrollRunId(Long payrollRunId);
}
