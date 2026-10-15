package com.hrm.backend.controller;

import com.hrm.backend.dto.response.LeaveBalanceListResponse;
import com.hrm.backend.dto.response.LeaveRequestListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.LeaveQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping({"/v1/me"})
@RequiredArgsConstructor
public class MyLeaveController {
    private final LeaveQueryService leaveQueryService;

    @GetMapping("/leave-balances")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<LeaveBalanceListResponse> getMyLeaveBalances(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam int year) {
        return ResponseEntity.ok(leaveQueryService.getMyLeaveBalances(currentUser.getAccount().getId(), year));
    }

    @GetMapping("/leave-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<PagedResponse<LeaveRequestListItemResponse>> getMyLeaveRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(leaveQueryService.getMyLeaveRequests(currentUser.getAccount().getId(), status,
                leaveTypeId, fromDate, toDate, page, size));
    }
}
