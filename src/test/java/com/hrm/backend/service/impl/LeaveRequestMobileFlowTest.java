package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreateLeaveRequest;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeaveRequestMobileFlowTest {
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final LeaveTypeRepository types = mock(LeaveTypeRepository.class);
    final LeaveBalanceRepository balances = mock(LeaveBalanceRepository.class);
    final LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
    final LeaveRequestStatusHistoryRepository histories = mock(LeaveRequestStatusHistoryRepository.class);
    final LeaveRequestNotificationService notifications = mock(LeaveRequestNotificationService.class);
    final LeaveAttachmentService attachments = mock(LeaveAttachmentService.class);
    final AccountRoleRepository roles = mock(AccountRoleRepository.class);
    final EmployeeAssignmentRepository assignments = mock(EmployeeAssignmentRepository.class);
    final LeaveQueryService queries = mock(LeaveQueryService.class);
    LeaveRequestSubmissionServiceImpl submit;
    LeaveRequestApprovalServiceImpl approve;
    LeaveRequestCancellationServiceImpl cancel;
    Employee owner = Employee.builder().id(10L).build();
    LeaveType type = LeaveType.builder().id(4L).isActive(true).isPaid(false).build();
    CreateLeaveRequest input;
    LeaveRequest saved;

    @BeforeEach void setup() {
        submit = new LeaveRequestSubmissionServiceImpl(employees, types, balances, requests, histories,
                new WeekdayLeaveDurationCalculator(), notifications, attachments);
        ReflectionTestUtils.setField(submit, "companyTimezone", "Asia/Ho_Chi_Minh");
        approve = new LeaveRequestApprovalServiceImpl(requests, balances, histories, employees, assignments, roles, notifications);
        cancel = new LeaveRequestCancellationServiceImpl(employees, requests, histories, queries);
        input = new CreateLeaveRequest(); input.setLeaveTypeId(4L);
        input.setStartDate(LocalDate.of(2099, 1, 5)); input.setEndDate(LocalDate.of(2099, 1, 6)); input.setReason(" Lý do ");
        when(employees.findByAccountId(1L)).thenReturn(Optional.of(owner));
        when(employees.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(types.findById(4L)).thenReturn(Optional.of(type));
        when(employees.findByAccountId(2L)).thenReturn(Optional.of(Employee.builder().id(20L).build()));
        when(roles.findByAccountIdWithRole(2L)).thenReturn(List.of(AccountRole.builder().role(Role.builder().code("HR").build()).build()));
        when(requests.saveAndFlush(any())).thenAnswer(inv -> { saved = inv.getArgument(0); saved.setId(3L); return saved; });
        when(requests.findWithEmployeeAndLeaveTypeByIdForUpdate(3L)).thenAnswer(inv -> Optional.ofNullable(saved));
    }

    @Test void unpaidSubmitThenApproveNeedsNoBalanceAndKeepsHistoryAndNotifications() {
        var result = submit.submit(1L, input);
        assertEquals("NP000003", result.getRequestCode());
        assertEquals("PENDING", result.getStatus());
        assertEquals("Lý do", saved.getReason());
        var approved = approve.approve(2L, 3L);
        assertEquals("APPROVED", approved.getStatus());
        assertNull(approved.getRemainingDays());
        verifyNoInteractions(balances);
        verify(notifications).notifySubmitted(saved);
        verify(notifications).notifyApproved(saved);
        verify(histories, times(2)).save(any());
        assertEquals(409, assertThrows(AuthException.class, () -> approve.approve(2L, 3L)).getStatus());
        assertEquals(409, assertThrows(AuthException.class, () -> cancel.cancel(1L, 3L, "Muộn")).getStatus());
    }

    @Test void paidLeaveStillReservesPendingAndDebitsOnlyAtApproval() {
        type.setIsPaid(true);
        var balance = LeaveBalance.builder().allocatedDays(new BigDecimal("10")).usedDays(new BigDecimal("2")).build();
        when(balances.findForUpdate(10L, 4L, (short) 2099)).thenReturn(Optional.of(balance));
        when(requests.sumRequestedDays(eq(10L), eq(4L), eq(LeaveRequestStatus.PENDING), any(), any())).thenReturn(new BigDecimal("3"));
        submit.submit(1L, input);
        assertEquals(new BigDecimal("2"), balance.getUsedDays());
        var result = approve.approve(2L, 3L);
        assertEquals(new BigDecimal("4"), balance.getUsedDays());
        assertEquals(new BigDecimal("6"), result.getRemainingDays());
    }

    @Test void paidInsufficientOrOverlapDoesNotWriteRequest() {
        type.setIsPaid(true);
        when(balances.findForUpdate(10L, 4L, (short) 2099)).thenReturn(Optional.of(LeaveBalance.builder()
                .allocatedDays(BigDecimal.ONE).usedDays(BigDecimal.ZERO).build()));
        when(requests.sumRequestedDays(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        assertEquals("LEAVE_BALANCE_INSUFFICIENT", assertThrows(AuthException.class, () -> submit.submit(1L, input)).getCode());
        when(requests.existsOverlappingRequest(any(), any(), any(), any())).thenReturn(true);
        assertEquals("LEAVE_REQUEST_OVERLAP", assertThrows(AuthException.class, () -> submit.submit(1L, input)).getCode());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test void invalidAttachmentStopsBeforeAnyBusinessWriteAndValidAttachmentUsesSameRequest() {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(attachments.validate(file)).thenThrow(new AuthException("LEAVE_ATTACHMENT_INVALID", "Sai ảnh", 400));
        assertThrows(AuthException.class, () -> submit.submit(1L, input, file));
        verifyNoInteractions(employees);
        verify(requests, never()).saveAndFlush(any());
        var image = LeaveRequestAttachment.builder().build();
        doReturn(image).when(attachments).validate(file);
        submit.submit(1L, input, file);
        verify(attachments).save(saved, image);
    }

    @Test void cancelTrimsReasonAndNeverChangesBalanceOrAllowsAnotherOwner() {
        submit.submit(1L, input);
        assertEquals(403, assertThrows(AuthException.class, () -> cancel.cancel(2L, 3L, "Khác chủ")).getStatus());
        cancel.cancel(1L, 3L, "  Thay đổi lịch  ");
        assertEquals(LeaveRequestStatus.CANCELLED, saved.getStatus());
        assertNotNull(saved.getCancelledAt());
        var capture = ArgumentCaptor.forClass(LeaveRequestStatusHistory.class);
        verify(histories, times(2)).save(capture.capture());
        assertEquals("Thay đổi lịch", capture.getValue().getNote());
        verifyNoInteractions(balances);
        assertEquals(409, assertThrows(AuthException.class, () -> cancel.cancel(1L, 3L)).getStatus());
        assertEquals(400, assertThrows(AuthException.class, () -> cancel.cancel(1L, 3L, "a".repeat(1001))).getStatus());
    }

    @Test void legacyCancelWithoutBodyAndBlankReasonRemainSupported() {
        submit.submit(1L, input);
        cancel.cancel(1L, 3L);
        saved.setStatus(LeaveRequestStatus.PENDING);
        cancel.cancel(1L, 3L, "   ");
        var capture = ArgumentCaptor.forClass(LeaveRequestStatusHistory.class);
        verify(histories, times(3)).save(capture.capture());
        assertNull(capture.getAllValues().get(1).getNote());
        assertNull(capture.getAllValues().get(2).getNote());
    }

    @Test void cancellationAndApprovalCompeteUnderSimulatedRepositoryTransactionLock() throws Exception {
        submit.submit(1L, input);
        // Mô phỏng repository giữ khóa tới cuối transaction; không khẳng định đã kiểm tra lock PostgreSQL thật.
        var rowLock = new ReentrantLock();
        when(requests.findWithEmployeeAndLeaveTypeByIdForUpdate(3L)).thenAnswer(inv -> {
            rowLock.lock(); return Optional.of(saved);
        });
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var approval = pool.submit(() -> compete(start, rowLock, () -> approve.approve(2L, 3L)));
            var cancellation = pool.submit(() -> compete(start, rowLock, () -> cancel.cancel(1L, 3L, "Đổi lịch")));
            start.countDown();
            assertEquals(1, approval.get(5, TimeUnit.SECONDS) + cancellation.get(5, TimeUnit.SECONDS));
        }
        assertTrue(Set.of(LeaveRequestStatus.APPROVED, LeaveRequestStatus.CANCELLED).contains(saved.getStatus()));
        verify(histories, times(2)).save(any());
        verifyNoInteractions(balances);
    }

    // Một lượt thành công, lượt còn lại phải nhận 409 sau khi đọc trạng thái mới dưới khóa mô phỏng.
    private int compete(CountDownLatch start, ReentrantLock lock, Runnable action) throws Exception {
        start.await();
        try { action.run(); return 1; }
        catch (AuthException ex) { assertEquals(409, ex.getStatus()); return 0; }
        finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
    }
}
