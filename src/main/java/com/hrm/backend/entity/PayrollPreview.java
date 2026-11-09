package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payroll_previews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PayrollPreview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;
    @Column(name = "payroll_year", nullable = false) private Short payrollYear;
    @Column(name = "payroll_month", nullable = false) private Short payrollMonth;
    @Column(name = "input_fingerprint", nullable = false, length = 128) private String inputFingerprint;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_payroll_run_id") private PayrollRun createdPayrollRun;
    @Column(name = "consumed_at") private OffsetDateTime consumedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;

    @PrePersist void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
