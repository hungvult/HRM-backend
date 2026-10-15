package com.hrm.backend.controller;

import com.hrm.backend.dto.response.LeaveRequestDetailResponse;
import com.hrm.backend.dto.response.LeaveRequestListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.request.CreateLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestSubmissionResponse;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.LeaveQueryService;
import com.hrm.backend.service.LeaveRequestSubmissionService;
import com.hrm.backend.service.LeaveRequestCancellationService;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping({"/v1/leave-requests"})
@RequiredArgsConstructor
public class LeaveRequestController {
    private final LeaveQueryService leaveQueryService;
    private final LeaveRequestSubmissionService leaveRequestSubmissionService;
    private final LeaveRequestCancellationService leaveRequestCancellationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestSubmissionResponse> submitLeaveRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateLeaveRequest request) {
        LeaveRequestSubmissionResponse response = leaveRequestSubmissionService.submit(currentUser.getAccount().getId(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                        .buildAndExpand(response.getId()).toUri())
                .body(response);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestDetailResponse> cancelLeaveRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(leaveRequestCancellationService.cancel(currentUser.getAccount().getId(), id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveRequestDetailResponse> getLeaveRequest(@PathVariable Long id,
                                                                        @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(leaveQueryService.getLeaveRequestDetail(currentUser.getAccount().getId(), id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
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
