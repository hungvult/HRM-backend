package com.hrm.backend.controller;

import com.hrm.backend.dto.response.LeaveTypeResponse;
import com.hrm.backend.service.LeaveQueryService;
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
public class LeaveTypeController {
    private final LeaveQueryService leaveQueryService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<LeaveTypeResponse>> getActiveLeaveTypes() {
        return ResponseEntity.ok(leaveQueryService.getActiveLeaveTypes());
    }
}
