package com.hrm.backend.controller;

import com.hrm.backend.dto.response.MonthlyAttendanceResponse;
import com.hrm.backend.dto.response.TodayAttendanceResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me/attendance")
@RequiredArgsConstructor
public class MyAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/today")
    @Operation(summary = "Xem trạng thái chấm công hôm nay của nhân viên đang đăng nhập")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trả trạng thái hôm nay, kể cả khi chưa chấm công"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Tài khoản không có hồ sơ nhân viên")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TodayAttendanceResponse> getToday(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(attendanceService.getTodayAttendance(userDetails.getAccount().getId()));
    }

    @GetMapping
    @Operation(summary = "Xem bảng công theo tháng của nhân viên đang đăng nhập")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trả danh sách bản ghi trong tháng, có thể rỗng"),
            @ApiResponse(responseCode = "400", description = "month không đúng định dạng YYYY-MM"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực"),
            @ApiResponse(responseCode = "403", description = "Tài khoản không có hồ sơ nhân viên")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MonthlyAttendanceResponse> getMonthly(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Parameter(description = "Tháng cần xem, định dạng YYYY-MM", example = "2026-10")
            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(attendanceService.getMonthlyAttendance(userDetails.getAccount().getId(), month));
    }
}
