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
    private PayrollRunStatus status = PayrollRunStatus.DRAFT;

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

    @Column(name = "revision", nullable = false)
    private Integer revision = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_payroll_run_id")
    private PayrollRun supersedesPayrollRun;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @Column(name = "superseded_at")
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
