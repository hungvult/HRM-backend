package com.hrm.backend.controller;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.Account;
import com.hrm.backend.exception.ApiExceptionHandler;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobileSrsContractTest {
    final LeaveRequestSubmissionService submission = mock(LeaveRequestSubmissionService.class);
    final LeaveRequestCancellationService cancellation = mock(LeaveRequestCancellationService.class);
    final LeaveAttachmentService attachments = mock(LeaveAttachmentService.class);
    final PayrollRunService payrolls = mock(PayrollRunService.class);
    final NotificationService notices = mock(NotificationService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        var principal = new CustomUserDetails(Account.builder().id(42L).username("employee").build(), List.of("EMPLOYEE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(
                new LeaveRequestController(mock(LeaveQueryService.class), submission, cancellation,
                        mock(LeaveRequestApprovalService.class), mock(LeaveRequestRejectionService.class), attachments),
                new PayrollRunController(payrolls), new NotificationController(notices))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private String json() { return "{\"leaveTypeId\":4,\"startDate\":\"2099-01-05\",\"endDate\":\"2099-01-06\",\"reason\":\"Lý do\"}"; }

    @Test void legacyJsonAndMultipartOptionalImageBothCreate201() throws Exception {
        var response = LeaveRequestSubmissionResponse.builder().id(3L).requestCode("NP000003").status("PENDING").build();
        when(submission.submit(eq(42L), any())).thenReturn(response);
        when(submission.submit(eq(42L), any(), any())).thenReturn(response);
        mvc.perform(post("/v1/leave-requests").contentType(MediaType.APPLICATION_JSON).content(json()))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/v1/leave-requests/3"));
        var part = new MockMultipartFile("request", "", "application/json", json().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/v1/leave-requests").file(part)).andExpect(status().isCreated());
        mvc.perform(multipart("/v1/leave-requests").file(part)
                .file(new MockMultipartFile("attachment", "a.png", "image/png", LeaveAttachmentServiceTestBytes.png())))
                .andExpect(status().isCreated());
        verify(submission).submit(eq(42L), any());
        verify(submission).submit(eq(42L), any(), isNull());
    }

    @Test void invalidMultipartMissingJsonAndMultipleImagesAre400() throws Exception {
        mvc.perform(multipart("/v1/leave-requests")).andExpect(status().isBadRequest());
        var part = new MockMultipartFile("request", "", "application/json", json().getBytes());
        mvc.perform(multipart("/v1/leave-requests").file(part)
                .file(new MockMultipartFile("attachment", "a.png", "image/png", new byte[]{1}))
                .file(new MockMultipartFile("attachment", "b.png", "image/png", new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEAVE_ATTACHMENT_INVALID"));
        verifyNoInteractions(submission);
    }

    @Test void malformedMultipartJsonAndInvalidFieldsAre400AndWrongMimeIs415() throws Exception {
        mvc.perform(multipart("/v1/leave-requests")
                .file(new MockMultipartFile("request", "", "application/json", "{".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
        mvc.perform(multipart("/v1/leave-requests")
                .file(new MockMultipartFile("request", "", "application/json", "{}".getBytes())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(multipart("/v1/leave-requests")
                .file(new MockMultipartFile("request", "", "text/plain", json().getBytes())))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(submission);
    }

    @Test void legacyCancellationNoBodyAndOptionalReasonStayCompatible() throws Exception {
        when(cancellation.cancel(eq(42L), eq(3L), any())).thenReturn(LeaveRequestDetailResponse.builder().id(3L).status("CANCELLED").build());
        mvc.perform(post("/v1/leave-requests/3/cancel")).andExpect(status().isOk());
        mvc.perform(post("/v1/leave-requests/3/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thay đổi lịch\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/leave-requests/3/cancel").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(cancellation).cancel(42L, 3L, null);
        verify(cancellation).cancel(42L, 3L, "Thay đổi lịch");
    }

    @Test void byPeriodStaticRouteWinsOverRunIdAndUsesPrincipal() throws Exception {
        when(payrolls.myPayslipByPeriod(42L, (short) 2026, (short) 10))
                .thenReturn(new MyPayslipResponse(7L, (short) 2026, (short) 10, null, null));
        mvc.perform(get("/v1/payroll/my-payslips/by-period").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payrollRunId").value(7))
                .andExpect(jsonPath("$.year").value(2026));
        verify(payrolls).myPayslipByPeriod(42L, (short) 2026, (short) 10);
        verify(payrolls, never()).myPayslip(any(), any());
        mvc.perform(get("/v1/payroll/my-payslips/by-period").param("year", "2026")).andExpect(status().isBadRequest());
    }

    @Test void unreadRouteUsesAuthenticatedAccount() throws Exception {
        when(notices.markMyNotificationUnread(42L, 3L)).thenReturn(NotificationResponse.builder().id(3L).read(false).build());
        mvc.perform(patch("/v1/notifications/3/unread")).andExpect(status().isOk()).andExpect(jsonPath("$.read").value(false));
        verify(notices).markMyNotificationUnread(42L, 3L);
    }

    @Test void attachmentIsPrivateBinaryWithNoStoreAndSameAuthenticatedAccount() throws Exception {
        byte[] data = LeaveAttachmentServiceTestBytes.png();
        when(attachments.read(42L, 3L)).thenReturn(com.hrm.backend.entity.LeaveRequestAttachment.builder()
                .data(data).fileName("image.png").contentType("image/png").sizeBytes((long) data.length).build());
        mvc.perform(get("/v1/leave-requests/3/attachment")).andExpect(status().isOk())
                .andExpect(content().bytes(data)).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(attachments).read(42L, 3L);
    }

    static class LeaveAttachmentServiceTestBytes {
        static byte[] png() { return java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="); }
    }
}
