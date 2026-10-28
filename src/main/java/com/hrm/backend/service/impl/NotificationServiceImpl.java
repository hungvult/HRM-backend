package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.NotificationResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.Notification;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.NotificationRepository;
import com.hrm.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {
    private final NotificationRepository notifications;

    @Override
    public PagedResponse<NotificationResponse> getMyNotifications(Long accountId, int page, int size) {
        validatePage(page, size);
        Page<Notification> result = notifications.findByRecipientAccountId(accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        return PagedResponse.<NotificationResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).hasNext(result.hasNext()).build();
    }

    @Override
    public long getUnreadCount(Long accountId) {
        return notifications.countByRecipientAccountIdAndIsReadFalse(accountId);
    }

    @Override
    public NotificationResponse getMyNotification(Long accountId, Long notificationId) {
        return toResponse(findOwned(notificationId, accountId));
    }

    @Override
    @Transactional
    public NotificationResponse markMyNotificationRead(Long accountId, Long notificationId) {
        Notification notification = findOwned(notificationId, accountId);
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
        }
        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllMyNotificationsRead(Long accountId) {
        return notifications.markAllRead(accountId, OffsetDateTime.now());
    }

    private Notification findOwned(Long notificationId, Long accountId) {
        return notifications.findByIdAndRecipientAccountId(notificationId, accountId)
                // Do not reveal that another account owns the notification.
                .orElseThrow(() -> new ResourceNotFoundException("NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo."));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse.RelatedEntity related = notification.getRelatedEntityType() == null
                ? null : NotificationResponse.RelatedEntity.builder().type(notification.getRelatedEntityType())
                .id(notification.getRelatedEntityId()).build();
        return NotificationResponse.builder().id(notification.getId()).type(notification.getNotificationType())
                .title(notification.getTitle()).content(notification.getContent()).relatedEntity(related)
                .read(Boolean.TRUE.equals(notification.getIsRead())).readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt()).build();
    }
}
