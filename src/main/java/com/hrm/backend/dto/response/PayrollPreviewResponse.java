package com.hrm.backend.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record PayrollPreviewResponse(String previewToken, OffsetDateTime expiresAt, Short year, Short month,
                                     boolean periodClosed, String status, boolean canConfirm,
                                     PayrollWorkResponse workStandard, PayrollSummaryResponse summary,
                                     List<PayrollPreviewItemResponse> items, List<PayrollIssueResponse> issues) { }
