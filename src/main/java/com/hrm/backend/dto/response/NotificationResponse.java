package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class NotificationResponse {
    Long id;
    String type;
    String title;
    String content;
    RelatedEntity relatedEntity;
    boolean read;
    OffsetDateTime readAt;
    OffsetDateTime createdAt;

    @Value
    @Builder
    public static class RelatedEntity {
        String type;
        Long id;
    }
}
