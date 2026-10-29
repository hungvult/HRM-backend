package com.hrm.backend.service;

import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PayrollViewResponses;

public interface PayrollViewService {
    PayrollViewResponses.RunDetail getRun(Long runId);

    PayrollViewResponses.ItemPage getRunItems(Long runId, String q, Long departmentId, int page, int size);

    PagedResponse<PayrollViewResponses.PayslipListItem> getMyPayslips(
            Long accountId, Short year, int page, int size);

    PayrollViewResponses.PayslipDetail getMyPayslip(Long accountId, Long payrollRunId);
}
