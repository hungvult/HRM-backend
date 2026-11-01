package com.hrm.backend.dto.response;

import java.time.OffsetDateTime;

public record MyPayslipResponse(Long payrollRunId, Short year, Short month, PayrollPreviewItemResponse item,
                                OffsetDateTime publishedAt) { }
