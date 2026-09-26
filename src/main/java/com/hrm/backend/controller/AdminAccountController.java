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

@RestController
@RequestMapping("/v1/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Thiếu, sai hoặc hết hạn access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Chỉ ADMIN được phép truy cập", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})

public class AdminAccountController {
    private final AccountAdminService service;
    @PostMapping
    @Operation(summary = "Tạo tài khoản", description = "Chỉ ADMIN. Có thể truyền employeeId của hồ sơ nhân viên đã tạo; một nhân viên chỉ được liên kết với một tài khoản. Mật khẩu được BCrypt-hash trước khi lưu.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo"),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không phải ADMIN", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy nhân viên", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Username/email đã tồn tại hoặc nhân viên đã có tài khoản", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AdminAccountResponse> create(@AuthenticationPrincipal CustomUserDetails actor, @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(actor.getAccount().getId(), request));
    }

}
