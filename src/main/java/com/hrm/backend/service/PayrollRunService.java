package com.hrm.backend.service;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.dto.response.*;

public interface PayrollRunService {
    PayrollPreviewResponse preview(Long accountId, CreatePayrollPreviewRequest request);
    PayrollConfirmationResult confirm(Long accountId, ConfirmPayrollPreviewRequest request);
    PayrollRunResponse publish(Long accountId, Long runId);
    MyPayslipResponse myPayslip(Long accountId, Long runId);
}
