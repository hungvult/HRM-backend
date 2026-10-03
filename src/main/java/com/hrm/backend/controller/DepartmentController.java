package com.hrm.backend.controller;

import com.hrm.backend.dto.request.CreateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentStatusRequest;
import com.hrm.backend.dto.response.DepartmentResponse;
import com.hrm.backend.dto.response.ErrorResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.DepartmentStatus;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.DepartmentService;
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
@RequestMapping({"/v1/departments"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {
    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Tạo phòng ban", description = "Chỉ ADMIN/HR. Mã phòng ban do người dùng nhập, được chuẩn hóa thành chữ hoa và phải là duy nhất.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Đã tạo phòng ban"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép tạo", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Mã hoặc tên phòng ban đang hoạt động đã tồn tại", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DepartmentResponse> createDepartment(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(departmentService.createDepartment(currentUser.getAccount().getId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Cập nhật phòng ban", description = "Chỉ ADMIN/HR. Mã và trạng thái phòng ban không được thay đổi qua API này.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã cập nhật phòng ban"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép cập nhật", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy phòng ban", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Tên phòng ban đang hoạt động đã tồn tại", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DepartmentResponse> updateDepartment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(currentUser.getAccount().getId(), id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Kích hoạt hoặc vô hiệu hóa phòng ban", description = "Chỉ ADMIN/HR. Không thể vô hiệu hóa phòng ban đang có nhân viên được phân công hiện hành.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đã cập nhật trạng thái phòng ban"),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ hoặc trạng thái không thay đổi", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép cập nhật", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy phòng ban", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Phòng ban đang có nhân viên được phân công", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DepartmentResponse> updateDepartmentStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateDepartmentStatusRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartmentStatus(currentUser.getAccount().getId(), id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Xem chi tiết phòng ban", description = "Chỉ ADMIN/HR. Trả thông tin phòng ban theo ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy thông tin phòng ban thành công"),
            @ApiResponse(responseCode = "400", description = "ID phòng ban không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép xem", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy phòng ban", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<DepartmentResponse> getDepartment(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartment(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Danh sách phòng ban", description = "Chỉ ADMIN/HR. Tìm theo mã hoặc tên; có thể lọc trạng thái, phân trang và sắp xếp.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lấy danh sách thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số truy vấn không hợp lệ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai access token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Chỉ ADMIN/HR được phép xem", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagedResponse<DepartmentResponse>> searchDepartments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DepartmentStatus status,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return ResponseEntity.ok(departmentService.searchDepartments(q, status, page, size, sortBy, sortDirection));
    }
}
