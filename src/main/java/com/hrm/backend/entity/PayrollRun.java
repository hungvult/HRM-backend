package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import com.hrm.backend.entity.enums.PayrollRunStatus;

@Entity
@Table(name = "payroll_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollRun {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_year", nullable = false)
    private Short payrollYear;

    @Column(name = "payroll_month", nullable = false)
    private Short payrollMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayrollRunStatus status = PayrollRunStatus.GENERATED;

    /** C: standard Monday-Friday work days captured when this run is generated. */
    @Column(name = "standard_working_days")
    private Integer standardWorkingDays;

    /** M: paid standard minutes in one work day captured when this run is generated. */
    @Column(name = "standard_minutes_per_day")
    private Integer standardMinutesPerDay;

    @Column(name = "calculation_rule_version", length = 50)
    private String calculationRuleVersion;

    /** Opaque fingerprint used to reject a preview whose source data has changed. */
    @Column(name = "input_fingerprint", length = 128)
    private String inputFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by_account_id")
    private Account generatedByAccount;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by_account_id")
    private Account publishedByAccount;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    /** @deprecated Kept only to read legacy rows; MVP always writes revision 1. */
    @Deprecated
    @Column(name = "revision", nullable = false, updatable = false)
    private Integer revision = 1;

    /** @deprecated The MVP has no replacement/revision flow. */
    @Deprecated
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_payroll_run_id")
    private PayrollRun supersedesPayrollRun;

    /** @deprecated Kept only to read legacy rows; MVP always writes true. */
    @Deprecated
    @Column(name = "is_current", nullable = false, updatable = false)
    private Boolean isCurrent = true;

    /** @deprecated The MVP has no replacement/revision flow. */
    @Deprecated
    @Column(name = "superseded_at", updatable = false)
    private OffsetDateTime supersededAt;

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
