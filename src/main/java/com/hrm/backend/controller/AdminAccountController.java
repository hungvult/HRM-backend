package com.hrm.backend.controller;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.AdminAccountResponse;
import com.hrm.backend.dto.response.AccountDetailResponse;
import com.hrm.backend.dto.response.EmployeeDto;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.dto.response.ReplaceAccountRolesResponse;
import com.hrm.backend.dto.response.UpdateAccountStatusResponse;
import com.hrm.backend.dto.response.UpdateAccountResponse;
import com.hrm.backend.entity.enums.AccountStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.AccountAdminService;
import com.hrm.backend.service.EmployeeService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/accounts") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") @SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Thiếu, sai hoặc hết hạn access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Chỉ ADMIN được phép truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class AdminAccountController {
    private final AccountAdminService service;
    private final EmployeeService employeeService;
    @PostMapping
    @Operation(summary = "Tạo tài khoản", description = "Chỉ ADMIN. Có thể truyền employeeId của hồ sơ nhân viên đã tạo; một nhân viên chỉ được liên kết với một tài khoản. Mật khẩu được BCrypt-hash trước khi lưu.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo"), @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(implementation = ErrorResponse.class))), @ApiResponse(responseCode = "403", description = "Không phải ADMIN", content = @Content(schema = @Schema(implementation = ErrorResponse.class))), @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên", content = @Content(schema = @Schema(implementation = ErrorResponse.class))), @ApiResponse(responseCode = "409", description = "Username/email đã tồn tại hoặc nhân viên đã có tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<AdminAccountResponse> create(@AuthenticationPrincipal CustomUserDetails actor, @Valid @RequestBody CreateAccountRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor.getAccount().getId(), request)); }
    @GetMapping
    @Operation(summary = "Danh sách tài khoản", description = "Chỉ ADMIN. Hỗ trợ phân trang, keyword, status và role.")
    public Page<AdminAccountResponse> list(@RequestParam(required = false) String keyword, @RequestParam(required = false) AccountStatus status, @RequestParam(required = false) String role, @PageableDefault(size = 20, sort = "id") Pageable pageable) { return service.search(keyword, status, role, pageable); }
    @GetMapping("/{accountId}")
    @Operation(summary = "Chi tiết tài khoản", description = "Trả thông tin tài khoản và hồ sơ nhân viên đầy đủ nếu account đã được liên kết.")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Thành công"), @ApiResponse(responseCode = "404", description = "Không tìm thấy account", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public AccountDetailResponse get(@PathVariable Long accountId, @AuthenticationPrincipal CustomUserDetails actor) {
        AdminAccountResponse account = service.get(accountId);
        EmployeeDto employee = account.getEmployeeId() == null ? null
                : employeeService.getEmployee(actor.getAccount().getId(), account.getEmployeeId());
        return AccountDetailResponse.builder()
                .id(account.getId())
                .username(account.getUsername())
                .email(account.getEmail())
                .status(account.getStatus())
                .roles(account.getRoles())
                .employee(employee)
                .build();
    }
    @PatchMapping("/{accountId}")
    @Operation(summary = "Cập nhật username/email", description = "Response chỉ gồm id, username, email và status.")
    public UpdateAccountResponse update(@AuthenticationPrincipal CustomUserDetails actor, @PathVariable Long accountId, @Valid @RequestBody UpdateAccountRequest request) {
        AdminAccountResponse updated = service.update(actor.getAccount().getId(), accountId, request);
        return UpdateAccountResponse.builder()
                .id(updated.getId())
                .username(updated.getUsername())
                .email(updated.getEmail())
                .status(updated.getStatus())
                .build();
    }
    @PatchMapping("/{accountId}/status")
    @Operation(summary = "Khóa, mở khóa hoặc vô hiệu hóa tài khoản", description = "LOCKED/DISABLED thu hồi tất cả refresh session đang hoạt động. Response chỉ gồm id và status.")
    public UpdateAccountStatusResponse updateStatus(@AuthenticationPrincipal CustomUserDetails actor, @PathVariable Long accountId, @Valid @RequestBody UpdateAccountStatusRequest request) {
        AdminAccountResponse updated = service.updateStatus(actor.getAccount().getId(), accountId, request);
        return UpdateAccountStatusResponse.builder().id(updated.getId()).status(updated.getStatus()).build();
    }
    @PutMapping("/{accountId}/roles")
    @Operation(summary = "Thay thế role của tài khoản", description = "Thu hồi refresh session để quyền mới có hiệu lực khi đăng nhập lại. Response chỉ gồm id và roles.")
    public ReplaceAccountRolesResponse replaceRoles(@AuthenticationPrincipal CustomUserDetails actor, @PathVariable Long accountId, @Valid @RequestBody ReplaceAccountRolesRequest request) {
        AdminAccountResponse updated = service.replaceRoles(actor.getAccount().getId(), accountId, request);
        return ReplaceAccountRolesResponse.builder().id(updated.getId()).roles(updated.getRoles()).build();
    }
}
