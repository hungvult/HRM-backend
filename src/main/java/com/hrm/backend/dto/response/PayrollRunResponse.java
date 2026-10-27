package com.hrm.backend.dto.response;

import java.time.OffsetDateTime;

public record PayrollRunResponse(Long id, Short year, Short month, String status, PayrollWorkResponse workStandard,
                                 PayrollSummaryResponse summary, Long generatedByAccountId, OffsetDateTime generatedAt,
                                 Long publishedByAccountId, OffsetDateTime publishedAt) { }
