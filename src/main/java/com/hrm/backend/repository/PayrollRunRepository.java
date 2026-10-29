package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByPayrollYearAndPayrollMonth(Short year, Short month);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayrollRun p where p.id = :id")
    Optional<PayrollRun> findByIdForUpdate(@Param("id") Long id);
}
