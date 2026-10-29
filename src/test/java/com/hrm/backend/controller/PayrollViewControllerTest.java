package com.hrm.backend.controller;

import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PayrollViewResponses;
import com.hrm.backend.entity.Account;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.PayrollViewService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollViewControllerTest {
    @Test
    void runListDelegatesAllFiltersToTheService() {
        PayrollViewService service = mock(PayrollViewService.class);
        PayrollViewController controller = new PayrollViewController(service);
        PagedResponse<PayrollViewResponses.RunListItem> empty = PagedResponse
                .<PayrollViewResponses.RunListItem>builder().content(List.of()).page(0).size(20)
                .totalElements(0).totalPages(0).hasNext(false).build();
        when(service.getRuns((short) 2026, (short) 9, com.hrm.backend.entity.enums.PayrollRunStatus.GENERATED,
                null, null, null, null, 0, 20)).thenReturn(empty);

        var response = controller.getRuns((short) 2026, (short) 9,
                com.hrm.backend.entity.enums.PayrollRunStatus.GENERATED, null, null, null, null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(empty, response.getBody());
        verify(service).getRuns((short) 2026, (short) 9, com.hrm.backend.entity.enums.PayrollRunStatus.GENERATED,
                null, null, null, null, 0, 20);
    }

    @Test
    void personalPayslipListUsesTheAuthenticatedAccountAndReturnsAnEmptyPage() {
        PayrollViewService service = mock(PayrollViewService.class);
        PayrollViewController controller = new PayrollViewController(service);
        CustomUserDetails principal = new CustomUserDetails(Account.builder().id(42L).username("employee.a").build(),
                List.of("EMPLOYEE"));
        PagedResponse<PayrollViewResponses.PayslipListItem> empty = PagedResponse
                .<PayrollViewResponses.PayslipListItem>builder().content(List.of()).page(0).size(20)
                .totalElements(0).totalPages(0).hasNext(false).build();
        when(service.getMyPayslips(42L, (short) 2026, 0, 20)).thenReturn(empty);

        var response = controller.getMyPayslips(principal, (short) 2026, 0, 20);

        assertEquals(empty, response.getBody());
        verify(service).getMyPayslips(42L, (short) 2026, 0, 20);
    }
}
