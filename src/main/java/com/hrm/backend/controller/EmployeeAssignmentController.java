package com.hrm.backend.controller;

import com.hrm.backend.dto.request.CreateEmployeeAssignmentRequest;
import com.hrm.backend.dto.response.EmployeeAssignmentResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.EmployeeAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/employee-assignments"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EmployeeAssignmentController {
    private final EmployeeAssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR')")
    @Operation(summary = "Phân công nhân viên", description = "Tạo phân công hiện hành và tự đóng phân công cũ của nhân viên.")
    public ResponseEntity<EmployeeAssignmentResponse> createAssignment(@AuthenticationPrincipal CustomUserDetails currentUser,
                                                                         @Valid @RequestBody CreateEmployeeAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assignmentService.createAssignment(currentUser.getAccount().getId(), request));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Xem phân công hiện tại của tôi")
    public ResponseEntity<EmployeeAssignmentResponse> getCurrentAssignment(@AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(assignmentService.getCurrentAssignment(currentUser.getAccount().getId()));
    }
}
