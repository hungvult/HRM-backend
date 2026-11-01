package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Notification;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationUnreadTest {
    @Test void ownedUnreadIsIdempotentAndDoesNotChangeContent() {
        var repo = mock(NotificationRepository.class);
        var service = new NotificationServiceImpl(repo);
        var notice = Notification.builder().id(3L).title("Tiêu đề").content("Nội dung").isRead(true).readAt(OffsetDateTime.now()).build();
        when(repo.markUnread(3L, 1L)).thenAnswer(inv -> { notice.setIsRead(false); notice.setReadAt(null); return 1; });
        when(repo.findByIdAndRecipientAccountId(3L, 1L)).thenReturn(Optional.of(notice));
        for (int i = 0; i < 2; i++) {
            var result = service.markMyNotificationUnread(1L, 3L);
            assertFalse(result.isRead()); assertNull(result.getReadAt()); assertEquals("Nội dung", result.getContent());
        }
        verify(repo, times(2)).markUnread(3L, 1L);
    }

    @Test void wrongOwnerUses404AndDoesNotReadOtherNotification() {
        var repo = mock(NotificationRepository.class);
        assertEquals("NOTIFICATION_NOT_FOUND", assertThrows(ResourceNotFoundException.class,
                () -> new NotificationServiceImpl(repo).markMyNotificationUnread(2L, 3L)).getCode());
        verify(repo).markUnread(3L, 2L);
        verify(repo, never()).findByIdAndRecipientAccountId(anyLong(), anyLong());
    }
}
