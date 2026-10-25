package com.hrm.backend.controller;

import com.hrm.backend.dto.request.CreateSalaryProfileRequest;
import com.hrm.backend.dto.request.UpdateSalaryProfileRequest;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.dto.response.SalaryProfileEmployeeListItemResponse;
import com.hrm.backend.dto.response.SalaryProfileHistoryItemResponse;
import com.hrm.backend.dto.response.SalaryProfileHistoryResponse;
import com.hrm.backend.entity.enums.SalaryProfileState;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.PayrollSalaryProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payroll/salary-profiles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('HR')")
public class PayrollSalaryProfileController {
    private final PayrollSalaryProfileService salaryProfiles;

    @GetMapping("/employees")
    @Operation(summary = "Danh sách nhân viên và lương hiện hành",
            description = "Chỉ HR. Tìm theo mã/họ tên, lọc phòng ban hoặc trạng thái hồ sơ lương, có phân trang.")
    public ResponseEntity<PagedResponse<SalaryProfileEmployeeListItemResponse>> searchEmployees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) SalaryProfileState profileState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(salaryProfiles.searchEmployees(q, departmentId, profileState, page, size));
    }

    @GetMapping("/employees/{employeeId}")
    @Operation(summary = "Lịch sử mốc lương của nhân viên", description = "Chỉ HR. Mốc mới nhất được trả trước.")
    public ResponseEntity<SalaryProfileHistoryResponse> getEmployeeSalaryProfileHistory(@PathVariable Long employeeId) {
        return ResponseEntity.ok(salaryProfiles.getEmployeeSalaryProfileHistory(employeeId));
    }

    @PostMapping
    @Operation(summary = "Tạo mốc lương", description = "Chỉ HR. Tự khép mốc liền trước trong cùng transaction.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo mốc lương thành công",
                    content = @Content(schema = @Schema(implementation = SalaryProfileHistoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai kiểu dữ liệu",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền HR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mốc chồng lấn hoặc bị khóa bởi payroll",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Ngày hiệu lực hoặc khoản tiền không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SalaryProfileHistoryItemResponse> createSalaryProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateSalaryProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(salaryProfiles.createSalaryProfile(currentUser.getAccount().getId(), request));
    }

    @PutMapping("/{profileId}")
    @Operation(summary = "Cập nhật mốc lương chưa được sử dụng", description = "Chỉ HR. Toàn bộ mốc được thay thế; lịch sử payroll đã dùng không thể sửa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật mốc lương thành công",
                    content = @Content(schema = @Schema(implementation = SalaryProfileHistoryItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Thiếu hoặc sai kiểu dữ liệu",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền HR",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy mốc lương",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mốc chồng lấn hoặc bị khóa bởi payroll",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Ngày hiệu lực hoặc khoản tiền không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<SalaryProfileHistoryItemResponse> updateSalaryProfile(
            @PathVariable Long profileId,
            @Valid @RequestBody UpdateSalaryProfileRequest request) {
        return ResponseEntity.ok(salaryProfiles.updateSalaryProfile(profileId, request));
    }
}
