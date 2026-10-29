package com.hrm.backend.controller;

import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PayrollViewResponses;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.PayrollViewService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PayrollViewController {
    private final PayrollViewService payrollViews;

    @GetMapping("/runs/{runId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollViewResponses.RunDetail> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(payrollViews.getRun(runId));
    }

    @GetMapping("/runs/{runId}/items")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollViewResponses.ItemPage> getRunItems(
            @PathVariable Long runId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(payrollViews.getRunItems(runId, q, departmentId, page, size));
    }

    @GetMapping("/my-payslips")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<PayrollViewResponses.PayslipListItem>> getMyPayslips(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) Short year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(payrollViews.getMyPayslips(principal.getAccount().getId(), year, page, size));
    }

    @GetMapping("/my-payslips/{payrollRunId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayrollViewResponses.PayslipDetail> getMyPayslip(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long payrollRunId) {
        return ResponseEntity.ok(payrollViews.getMyPayslip(principal.getAccount().getId(), payrollRunId));
    }
}
