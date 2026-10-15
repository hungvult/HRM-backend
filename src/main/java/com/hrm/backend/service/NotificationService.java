package com.hrm.backend.service;

import com.hrm.backend.dto.response.NotificationResponse;
import com.hrm.backend.dto.response.PagedResponse;

public interface NotificationService {
    PagedResponse<NotificationResponse> getMyNotifications(Long accountId, int page, int size);

    long getUnreadCount(Long accountId);

    NotificationResponse getMyNotification(Long accountId, Long notificationId);

    NotificationResponse markMyNotificationRead(Long accountId, Long notificationId);

    int markAllMyNotificationsRead(Long accountId);
}
