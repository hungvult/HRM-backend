package com.hrm.backend.controller;

import com.hrm.backend.dto.response.LeaveTypeResponse;
import com.hrm.backend.service.LeaveQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/v1/leave-types"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LeaveTypeController {
    private final LeaveQueryService leaveQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    @Operation(summary = "Danh sách loại phép đang áp dụng")
    public ResponseEntity<List<LeaveTypeResponse>> getActiveLeaveTypes() {
        return ResponseEntity.ok(leaveQueryService.getActiveLeaveTypes());
    }
}
