package com.hrm.backend.controller;

import com.hrm.backend.entity.Account;
import com.hrm.backend.repository.*;
import com.hrm.backend.security.*;
import com.hrm.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {PayrollRunController.class, LeaveRequestController.class, NotificationController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestSecurityExceptionHandler.class, SecurityErrorResponseWriter.class})
class MobileSrsSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PayrollRunService payrolls;
    @MockitoBean LeaveQueryService leaveQueries;
    @MockitoBean LeaveRequestSubmissionService submissions;
    @MockitoBean LeaveRequestCancellationService cancellations;
    @MockitoBean LeaveRequestApprovalService approvals;
    @MockitoBean LeaveRequestRejectionService rejections;
    @MockitoBean LeaveAttachmentService attachments;
    @MockitoBean NotificationService notifications;
    @MockitoBean JwtService jwt;
    @MockitoBean CustomUserDetailsService users;
    @MockitoBean AuthSessionRepository sessions;
    @MockitoBean ApiLogRepository apiLogs;

    @Test void unauthenticatedCannotReadPayslipAttachmentOrChangeNotification() throws Exception {
        mvc.perform(get("/v1/payroll/my-payslips/by-period").param("year", "2026").param("month", "10"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/leave-requests/3/attachment")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/v1/notifications/3/unread")).andExpect(status().isUnauthorized());
        verifyNoInteractions(payrolls, attachments, notifications);
    }

    @Test void employeeCanUsePersonalByPeriodRouteButNotHrRunRoute() throws Exception {
        var principal = new CustomUserDetails(Account.builder().id(42L).username("employee").build(), List.of("EMPLOYEE"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        when(payrolls.myPayslipByPeriod(42L, (short) 2026, (short) 10))
                .thenReturn(new com.hrm.backend.dto.response.MyPayslipResponse(7L, (short) 2026, (short) 10, null, null));
        mvc.perform(get("/v1/payroll/my-payslips/by-period").param("year", "2026").param("month", "10").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payrollRunId").value(7));
        mvc.perform(post("/v1/payroll/runs/7/publish").with(authentication(auth))).andExpect(status().isForbidden());
        verify(payrolls, never()).publish(any(), any());
    }
}
