package com.hrm.backend.repository;

import com.hrm.backend.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

/** Kept separate from the write-side repository supplied by HRM-243. */
public interface PayrollRunViewRepository extends JpaRepository<PayrollRun, Long> {
}
