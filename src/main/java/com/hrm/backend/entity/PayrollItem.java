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

    /** Q: exact paid minutes; payroll calculation must use this instead of rounded days. */
    @Column(name = "paid_minutes")
    private Integer paidMinutes;

    /** Display-only Q/M value, retained to four decimal places. */
    @Column(name = "paid_working_days", precision = 9, scale = 4)
    private BigDecimal paidWorkingDays;

    @Column(name = "base_salary_snapshot", nullable = false, precision = 14, scale = 2)
    private BigDecimal baseSalarySnapshot;

    @Column(name = "earned_base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal earnedBaseSalary;

    @Column(name = "allowance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal allowanceAmount = BigDecimal.ZERO;

    /** Monthly allowance captured from the salary profile before proration. */
    @Column(name = "allowance_snapshot", precision = 15, scale = 0)
    private BigDecimal allowanceSnapshot;

    @Column(name = "deduction_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductionAmount = BigDecimal.ZERO;

    /** Monthly deduction captured from the salary profile before proration. */
    @Column(name = "deduction_snapshot", precision = 15, scale = 0)
    private BigDecimal deductionSnapshot;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary;

    @Column(name = "employee_code_snapshot", length = 30)
    private String employeeCodeSnapshot;

    @Column(name = "employee_full_name_snapshot", length = 200)
    private String employeeFullNameSnapshot;

    @Column(name = "department_id_snapshot")
    private Long departmentIdSnapshot;

    @Column(name = "department_code_snapshot", length = 30)
    private String departmentCodeSnapshot;

    @Column(name = "department_name_snapshot", length = 150)
    private String departmentNameSnapshot;

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
