package com.hrm.backend.controller;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceAdjustmentHistoryResponse;
import com.hrm.backend.dto.response.AttendanceManagementListItemResponse;
import com.hrm.backend.dto.response.AttendanceMutationResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.request.AdjustAttendanceRequest;
import com.hrm.backend.dto.request.CreateAttendanceRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/v1/attendances")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AttendanceManagementController {
    private final AttendanceManagementService attendanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Danh sách bảng công", description = "ADMIN/HR xem toàn bộ; MANAGER chỉ xem nhân viên trực thuộc.")
    public ResponseEntity<PagedResponse<AttendanceManagementListItemResponse>> list(
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

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Bổ sung công", description = "Chỉ tạo ngày công chưa có dữ liệu trong phạm vi quyền.")
    public ResponseEntity<AttendanceMutationResponse> create(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateAttendanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.createAttendance(currentUser.getAccount().getId(), request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Điều chỉnh công", description = "Cập nhật bản ghi công và lưu lịch sử thay đổi trong cùng giao dịch.")
    public ResponseEntity<AttendanceMutationResponse> adjust(@PathVariable Long id,
                                                               @AuthenticationPrincipal CustomUserDetails currentUser,
                                                               @Valid @RequestBody AdjustAttendanceRequest request) {
        return ResponseEntity.ok(attendanceService.adjustAttendance(currentUser.getAccount().getId(), id, request));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Operation(summary = "Lịch sử điều chỉnh công")
    public ResponseEntity<PagedResponse<AttendanceAdjustmentHistoryResponse>> history(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(attendanceService.getAdjustmentHistory(currentUser.getAccount().getId(), id, page, size));
    }
}
