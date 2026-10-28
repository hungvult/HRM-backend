package com.hrm.backend.controller;

import com.hrm.backend.dto.response.NotificationResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.UnreadNotificationCountResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/v1/notifications"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Danh sách thông báo cá nhân")
    public ResponseEntity<PagedResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getMyNotifications(user.getAccount().getId(), page, size));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo chưa đọc")
    public ResponseEntity<UnreadNotificationCountResponse> getUnreadCount(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(UnreadNotificationCountResponse.builder()
                .unreadCount(notificationService.getUnreadCount(user.getAccount().getId())).build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết thông báo cá nhân")
    public ResponseEntity<NotificationResponse> getNotification(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getMyNotification(user.getAccount().getId(), id));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markMyNotificationRead(user.getAccount().getId(), id));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả thông báo cá nhân đã đọc")
    public ResponseEntity<UnreadNotificationCountResponse> markAllRead(@AuthenticationPrincipal CustomUserDetails user) {
        notificationService.markAllMyNotificationsRead(user.getAccount().getId());
        return ResponseEntity.ok(UnreadNotificationCountResponse.builder().unreadCount(0).build());
    }
}
