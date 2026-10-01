package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "annual_quota_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualQuotaDays = BigDecimal.ZERO;

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
