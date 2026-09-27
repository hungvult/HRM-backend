package com.hrm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "api_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "c", columnDefinition = "TEXT")
    private String content;

    @Column(name = "cmd", length = 200)
    private String command;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "ip", length = 100)
    private String ip;

    @Column(name = "t", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "l", nullable = false, length = 30)
    private String level;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_url", columnDefinition = "TEXT")
    private String requestUrl;

    @Column(name = "token_code", columnDefinition = "TEXT")
    private String tokenCode;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }

        if (level == null || level.isBlank()) {
            level = "Information";
        }
    }
}
