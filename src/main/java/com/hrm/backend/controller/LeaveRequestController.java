package com.hrm.backend.controller;

import com.hrm.backend.dto.response.LeaveRequestDetailResponse;
import com.hrm.backend.dto.response.LeaveRequestListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.request.CreateLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestSubmissionResponse;
import com.hrm.backend.dto.response.LeaveRequestApprovalResponse;
import com.hrm.backend.dto.request.RejectLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestRejectionResponse;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.LeaveQueryService;
import com.hrm.backend.service.LeaveRequestSubmissionService;
import com.hrm.backend.service.LeaveRequestCancellationService;
import com.hrm.backend.service.LeaveRequestApprovalService;
import com.hrm.backend.service.LeaveRequestRejectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import com.hrm.backend.dto.request.CancelLeaveRequest;
import com.hrm.backend.service.LeaveAttachmentService;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping({"/v1/leave-requests"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LeaveRequestController {
    private final LeaveQueryService leaveQueryService;
    private final LeaveRequestSubmissionService leaveRequestSubmissionService;
    private final LeaveRequestCancellationService leaveRequestCancellationService;
    private final LeaveRequestApprovalService leaveRequestApprovalService;
    private final LeaveRequestRejectionService leaveRequestRejectionService;
    private final LeaveAttachmentService attachmentService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Gửi đơn nghỉ phép")
    // Giữ JSON cho Web/app cũ; ID nhân viên luôn lấy từ phiên qua service, không nhận từ payload.
    public ResponseEntity<LeaveRequestSubmissionResponse> submitLeaveRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateLeaveRequest request) {
        LeaveRequestSubmissionResponse response = leaveRequestSubmissionService.submit(currentUser.getAccount().getId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                        .buildAndExpand(response.getId()).toUri())
                .body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Gửi đơn nghỉ phép kèm một ảnh tùy chọn", description = "Part request là application/json; attachment là JPG/PNG/WebP tối đa 5 MB (5242880 byte).")
    // Mobile gửi JSON ở part request và tối đa một ảnh; service kiểm tra ảnh trước khi ghi cùng transaction với đơn.
    public ResponseEntity<LeaveRequestSubmissionResponse> submitMultipart(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestPart("request") CreateLeaveRequest request,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Chặn nhiều part cùng tên, tránh âm thầm chỉ nhận ảnh đầu và bỏ ảnh còn lại.
        if (httpRequest instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipart
                && multipart.getFiles("attachment").size() > 1) {
            throw new com.hrm.backend.exception.AuthException("LEAVE_ATTACHMENT_INVALID", "Mỗi đơn chỉ được có một ảnh minh chứng.", 400);
        }
        var response = leaveRequestSubmissionService.submit(currentUser.getAccount().getId(), request, attachment);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(response.getId()).toUri()).body(response);
    }

    @GetMapping("/{id}/attachment")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Đọc ảnh minh chứng theo cùng quyền xem đơn")
    // id là leave_requests.id, không phải ID file công khai; service kiểm tra phạm vi trước khi tải bytea.
    public ResponseEntity<byte[]> getAttachment(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        var image = attachmentService.read(currentUser.getAccount().getId(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(image.getData());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Hủy đơn nghỉ phép")
    // Body vắng giữ tương thích Web cũ; reason tùy chọn tối đa 1.000 ký tự được lưu ở lịch sử cùng giao dịch hủy.
    public ResponseEntity<LeaveRequestDetailResponse> cancelLeaveRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody(required = false) CancelLeaveRequest request) {
        return ResponseEntity.ok(leaveRequestCancellationService.cancel(currentUser.getAccount().getId(), id,
                request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER')")
    @Operation(summary = "Duyệt đơn nghỉ phép")
    public ResponseEntity<LeaveRequestApprovalResponse> approveLeaveRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(leaveRequestApprovalService.approve(currentUser.getAccount().getId(), id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER')")
    @Operation(summary = "Từ chối đơn nghỉ phép")
    public ResponseEntity<LeaveRequestRejectionResponse> rejectLeaveRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody RejectLeaveRequest request) {
        return ResponseEntity.ok(leaveRequestRejectionService.reject(currentUser.getAccount().getId(), id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Chi tiết đơn nghỉ phép")
    public ResponseEntity<LeaveRequestDetailResponse> getLeaveRequest(@PathVariable Long id,
                                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(leaveQueryService.getLeaveRequestDetail(currentUser.getAccount().getId(), id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Danh sách đơn nghỉ phép theo phạm vi")
    public ResponseEntity<PagedResponse<LeaveRequestListItemResponse>> getScopedLeaveRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(leaveQueryService.getScopedLeaveRequests(currentUser.getAccount().getId(), status,
                leaveTypeId, fromDate, toDate, page, size));
    }
}
