package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payroll_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salary_profile_id")
    private EmployeeSalaryProfile salaryProfile;

    @Column(name = "working_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal workingDays = BigDecimal.ZERO;

    @Column(name = "standard_working_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal standardWorkingDays;

    @Column(name = "base_salary_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseSalarySnapshot;

    @Column(name = "earned_base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal earnedBaseSalary;

    @Column(name = "allowance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allowanceAmount = BigDecimal.ZERO;

    @Column(name = "deduction_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
