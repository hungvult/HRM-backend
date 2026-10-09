package com.hrm.backend.controller;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AttendanceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/attendances")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AttendanceManagementController {
    private final AttendanceManagementService attendanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Danh sách bảng công", description = "ADMIN/HR xem toàn bộ; MANAGER chỉ xem nhân viên trực thuộc.")
    public ResponseEntity<PagedResponse<AttendanceListItemResponse>> list(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "workDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        return ResponseEntity.ok(attendanceService.searchAttendances(currentUser.getAccount().getId(), q, employeeId,
                departmentId, status, fromDate, toDate, page, size, sortBy, sortDirection));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Chi tiết bản ghi công", description = "ADMIN/HR xem mọi bản ghi; MANAGER chỉ xem nhân viên trực thuộc.")
    public ResponseEntity<AttendanceDetailResponse> getDetail(@PathVariable Long id,
                                                                 @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(attendanceService.getAttendance(currentUser.getAccount().getId(), id));
    }
}
