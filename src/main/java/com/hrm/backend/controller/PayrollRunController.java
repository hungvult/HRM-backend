package com.hrm.backend.controller;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.*;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.PayrollRunService;
import com.hrm.backend.service.PayrollConfirmationResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payroll")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PayrollRunController {
    private final PayrollRunService payrolls;

    @PostMapping("/previews")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollPreviewResponse> preview(@AuthenticationPrincipal CustomUserDetails principal,
                                                           @Valid @RequestBody CreatePayrollPreviewRequest request) {
        return ResponseEntity.ok(payrolls.preview(principal.getAccount().getId(), request));
    }

    @PostMapping("/runs")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollRunResponse> confirm(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @Valid @RequestBody ConfirmPayrollPreviewRequest request) {
        PayrollConfirmationResult result = payrolls.confirm(principal.getAccount().getId(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.run());
    }

    @PostMapping("/runs/{runId}/publish")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PayrollRunResponse> publish(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @PathVariable Long runId) {
        return ResponseEntity.ok(payrolls.publish(principal.getAccount().getId(), runId));
    }

    @GetMapping("/my-payslips/{runId}")
    public ResponseEntity<MyPayslipResponse> myPayslip(@AuthenticationPrincipal CustomUserDetails principal,
                                                        @PathVariable Long runId) {
        return ResponseEntity.ok(payrolls.myPayslip(principal.getAccount().getId(), runId));
    }
}
