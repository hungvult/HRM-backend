package com.hrm.backend.service;

import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PayrollViewResponses;
import com.hrm.backend.entity.enums.PayrollRunStatus;

public interface PayrollViewService {
    PagedResponse<PayrollViewResponses.RunListItem> getRuns(Short year, Short month, PayrollRunStatus status,
                                                             Short fromYear, Short fromMonth, Short toYear,
                                                             Short toMonth, int page, int size);

    PayrollViewResponses.RunDetail getRun(Long runId);

    PayrollViewResponses.ItemPage getRunItems(Long runId, String q, Long departmentId, int page, int size);

    PagedResponse<PayrollViewResponses.PayslipListItem> getMyPayslips(
            Long accountId, Short year, int page, int size);
}
