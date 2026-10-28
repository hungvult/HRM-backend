package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UnreadNotificationCountResponse {
    long unreadCount;
}
