package com.hrm.backend.controller;

import com.hrm.backend.dto.request.CreatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionStatusRequest;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PositionResponse;
import com.hrm.backend.entity.enums.PositionStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.PositionService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/positions"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PositionController {
    private final PositionService positionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Tạo chức vụ", description = "Chỉ ADMIN/HR. Hệ thống tự sinh mã theo dạng CV + ID sáu chữ số.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Đã tạo chức vụ"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép tạo", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tên chức vụ đang hoạt động đã tồn tại", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
    public ResponseEntity<PositionResponse> createPosition(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                            @Valid @RequestBody CreatePositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(positionService.createPosition(currentUser.getAccount().getId(), request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Danh sách chức vụ", description = "Chỉ ADMIN/HR. Tìm theo mã hoặc tên; có thể lọc, phân trang và sắp xếp.")
    public ResponseEntity<PagedResponse<PositionResponse>> searchPositions(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q, @RequestParam(required = false) PositionStatus status,
            @RequestParam(defaultValue = "name") String sortBy, @RequestParam(defaultValue = "asc") String sortDirection) {
        return ResponseEntity.ok(positionService.searchPositions(q, status, page, size, sortBy, sortDirection));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật chức vụ", description = "Chỉ ADMIN/HR. Mã và trạng thái không được thay đổi qua API này.")
    public ResponseEntity<PositionResponse> updatePosition(@PathVariable Long id,
                                                            @AuthenticationPrincipal CustomUserDetails currentUser,
                                                            @Valid @RequestBody UpdatePositionRequest request) {
        return ResponseEntity.ok(positionService.updatePosition(currentUser.getAccount().getId(), id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Kích hoạt hoặc vô hiệu hóa chức vụ", description = "Không thể vô hiệu hóa chức vụ đang có nhân viên được phân công hiện hành.")
    public ResponseEntity<PositionResponse> updatePositionStatus(@PathVariable Long id,
                                                                  @AuthenticationPrincipal CustomUserDetails currentUser,
                                                                  @Valid @RequestBody UpdatePositionStatusRequest request) {
        return ResponseEntity.ok(positionService.updatePositionStatus(currentUser.getAccount().getId(), id, request));
    }
}
