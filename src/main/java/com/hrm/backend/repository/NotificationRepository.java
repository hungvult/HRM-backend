package com.hrm.backend.repository;

import com.hrm.backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientAccountId(Long recipientAccountId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientAccountId(Long id, Long recipientAccountId);

    long countByRecipientAccountIdAndIsReadFalse(Long recipientAccountId);

    @Modifying
    @Query(value = "INSERT INTO notifications (recipient_account_id, notification_type, event_key, title, content, "
            + "related_entity_type, related_entity_id, is_read, created_at) "
            + "VALUES (:recipientAccountId, :notificationType, :eventKey, :title, :content, :relatedEntityType, "
            + ":relatedEntityId, false, CURRENT_TIMESTAMP) "
            + "ON CONFLICT (recipient_account_id, event_key) WHERE event_key IS NOT NULL DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("recipientAccountId") Long recipientAccountId,
                       @Param("notificationType") String notificationType,
                       @Param("eventKey") String eventKey,
                       @Param("title") String title,
                       @Param("content") String content,
                       @Param("relatedEntityType") String relatedEntityType,
                       @Param("relatedEntityId") Long relatedEntityId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.recipientAccount.id = :recipientAccountId AND n.isRead = false")
    int markAllRead(@Param("recipientAccountId") Long recipientAccountId, @Param("readAt") OffsetDateTime readAt);
}
