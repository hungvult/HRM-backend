package com.hrm.backend.controller;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @Operation(summary = "Ghi nhận check-in của nhân viên đang đăng nhập", description = "Luôn kiểm tra GPS hợp lệ. Khi tạm tắt app.attendance.wifi-verification-enabled, xác minh GPS tại địa điểm app.attendance.gps-work-location-id và bỏ đối chiếu IP Wi-Fi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu GPS không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Wi-Fi hoặc vị trí GPS không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Nhân viên đã check-in trong ngày")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AttendanceActionResponse> checkIn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceLocationRequest request,
            HttpServletRequest httpRequest) {
        AttendanceActionResponse response = attendanceService.checkIn(
                userDetails.getAccount().getId(), request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-out")
    @Operation(summary = "Ghi nhận check-out của nhân viên đang đăng nhập", description = "Luôn kiểm tra GPS mới và thứ tự check-in/out. Việc kiểm tra IP Wi-Fi phụ thuộc app.attendance.wifi-verification-enabled, không thay đổi dữ liệu request của app.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-out thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu GPS không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Wi-Fi hoặc vị trí GPS không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Chưa check-in hoặc đã check-out")
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<AttendanceActionResponse> checkOut(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceLocationRequest request,
            HttpServletRequest httpRequest) {
        AttendanceActionResponse response = attendanceService.checkOut(
                userDetails.getAccount().getId(), request, httpRequest);
        return ResponseEntity.ok(response);
    }
}
