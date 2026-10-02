package com.hrm.backend.controller;

import com.hrm.backend.dto.request.CreateEmployeeRequest;
import com.hrm.backend.dto.request.UpdateEmployeeStatusRequest;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.EmployeeListItemResponse;
import com.hrm.backend.dto.response.UpdateEmployeeStatusResponse;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/employees") @RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {
    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Tạo hồ sơ nhân viên", description = "Chỉ ADMIN/HR. Tạo hồ sơ trước, sau đó dùng id trả về làm employeeId khi tạo tài khoản.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo hồ sơ nhân viên"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép tạo", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mã nhân viên hoặc email đã tồn tại", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmployeeDto> createEmployee(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createEmployee(currentUser.getAccount().getId(), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Danh sách nhân viên", description = "Tìm kiếm theo mã, họ tên hoặc email; lọc theo trạng thái và phân công hiện hành.")
    public ResponseEntity<PagedResponse<EmployeeListItemResponse>> listEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EmploymentStatus employmentStatus,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(defaultValue = "fullName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return ResponseEntity.ok(employeeService.searchEmployees(q, employmentStatus, departmentId, positionId,
                page, size, sortBy, sortDirection));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Thay đổi trạng thái nhân viên")
    public ResponseEntity<UpdateEmployeeStatusResponse> updateEmployeeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateEmployeeStatusRequest request) {
        return ResponseEntity.ok(employeeService.updateEmployeeStatus(
                currentUser.getAccount().getId(), id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Xem hồ sơ nhân viên", description = "ADMIN/HR xem mọi hồ sơ; MANAGER chỉ xem nhân viên trực thuộc; EMPLOYEE chỉ xem hồ sơ của chính mình.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công"),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không đủ quyền hoặc vượt phạm vi dữ liệu", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<EmployeeDto> getEmployee(@PathVariable("id") Long employeeId, @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(employeeService.getEmployee(currentUser.getAccount().getId(), employeeId));
    }
}
