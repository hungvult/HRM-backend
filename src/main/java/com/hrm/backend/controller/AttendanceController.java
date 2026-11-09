package com.hrm.backend.controller;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.dto.response.AttendanceActionResponse;
import com.hrm.backend.dto.response.AttendancePrecheckResponse;
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

    @PostMapping("/pre-check")
    @Operation(summary = "Xác minh ngầm điều kiện chấm công", description = "Dùng cùng chính sách Wi-Fi/GPS với check-in/out, nhận {} nếu không lấy được GPS. Không ghi công, không cấp token cho lượt chấm công sau. IP lấy từ kết nối/proxy tin cậy; tài khoản lấy từ phiên đăng nhập.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bằng chứng hiện tại đạt chính sách server"),
            @ApiResponse(responseCode = "400", description = "Nhóm GPS thiếu hoặc sai định dạng"),
            @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc phiên hết hiệu lực"),
            @ApiResponse(responseCode = "403", description = "Nhân viên hoặc bằng chứng không đủ điều kiện")
    })
    @SecurityRequirement(name = "bearerAuth")
    // App gửi GPS khi màn chấm công đang mở. ID accounts lấy từ principal, không nhận ID nhân viên hay IP client tự khai.
    // Service chỉ xác minh; lỗi dùng cùng mã với check-in/out để không tạo một chính sách riêng cho kiểm tra ngầm.
    public ResponseEntity<AttendancePrecheckResponse> preCheck(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceLocationRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(attendanceService.preCheck(userDetails.getAccount().getId(), request, httpRequest));
    }

    @PostMapping("/check-in")
    @Operation(summary = "Ghi nhận check-in của nhân viên đang đăng nhập", description = "app.attendance.verification-mode mặc định AND (Wi-Fi và GPS), OR cho phép Wi-Fi hoặc GPS; có thể gửi {} khi không có GPS. Nếu gửi GPS phải đủ cả nhóm. wifi-verification-enabled=false giữ GPS-only tại gps-work-location-id. Chỉ lưu bằng chứng đã xác minh.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu GPS không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Wi-Fi hoặc vị trí GPS không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Nhân viên đã check-in trong ngày")
    })
    @SecurityRequirement(name = "bearerAuth")
    // accountId lấy từ phiên; DTO cho phép bỏ toàn bộ GPS, service quyết định Wi-Fi/GPS theo cấu hình server.
    public ResponseEntity<AttendanceActionResponse> checkIn(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceLocationRequest request,
            HttpServletRequest httpRequest) {
        AttendanceActionResponse response = attendanceService.checkIn(
                userDetails.getAccount().getId(), request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-out")
    @Operation(summary = "Ghi nhận check-out của nhân viên đang đăng nhập", description = "Giữ thứ tự check-in/out và giờ server. verification-mode=AND yêu cầu Wi-Fi và GPS; OR cho phép Wi-Fi hoặc GPS, nhận {} khi thiếu GPS. wifi-verification-enabled=false giữ GPS-only. Không lưu GPS không vượt qua xác minh.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-out thành công"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu GPS không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Wi-Fi hoặc vị trí GPS không hợp lệ"),
            @ApiResponse(responseCode = "409", description = "Chưa check-in hoặc đã check-out")
    })
    @SecurityRequirement(name = "bearerAuth")
    // Dùng cùng hợp đồng bằng chứng với check-in; không nhận giờ công hay ID nhân viên do client khai.
    public ResponseEntity<AttendanceActionResponse> checkOut(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AttendanceLocationRequest request,
            HttpServletRequest httpRequest) {
        AttendanceActionResponse response = attendanceService.checkOut(
                userDetails.getAccount().getId(), request, httpRequest);
        return ResponseEntity.ok(response);
    }
}
