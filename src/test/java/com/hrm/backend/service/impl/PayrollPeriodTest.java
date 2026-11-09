package com.hrm.backend.service.impl;

import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import com.hrm.backend.exception.*;
import com.hrm.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollPeriodTest {
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final PayrollRunRepository runs = mock(PayrollRunRepository.class);
    final PayrollItemRepository items = mock(PayrollItemRepository.class);
    final PayrollRunServiceImpl service = new PayrollRunServiceImpl(mock(AccountRepository.class), employees,
            mock(EmployeeSalaryProfileRepository.class), mock(EmployeeAssignmentRepository.class), mock(AttendanceRecordRepository.class),
            mock(LeaveRequestRepository.class), runs, items, mock(PayrollPreviewRepository.class));
    PayrollRun run;
    @BeforeEach void setup() {
        when(employees.findByAccountId(1L)).thenReturn(Optional.of(Employee.builder().id(10L).build()));
        run = PayrollRun.builder().id(7L).payrollYear((short) 2026).payrollMonth((short) 10)
                .status(PayrollRunStatus.PUBLISHED).standardWorkingDays(22).standardMinutesPerDay(480)
                .publishedAt(OffsetDateTime.parse("2026-11-01T00:00:00Z")).build();
        when(runs.findByPayrollYearAndPayrollMonth((short) 2026, (short) 10)).thenReturn(Optional.of(run));
    }

    @Test void sameShapeAndSnapshotsAsExistingDetailWithoutReadingAnotherEmployee() {
        PayrollItem item = PayrollItem.builder().payrollRun(run).employee(Employee.builder().id(10L).fullName("Tên hiện tại").build())
                .employeeCodeSnapshot("E001").employeeFullNameSnapshot("Tên snapshot").paidMinutes(480)
                .paidWorkingDays(BigDecimal.ONE).baseSalarySnapshot(new BigDecimal("22000000"))
                .allowanceSnapshot(BigDecimal.ZERO).deductionSnapshot(BigDecimal.ZERO).earnedBaseSalary(new BigDecimal("1000000"))
                .allowanceAmount(BigDecimal.ZERO).deductionAmount(BigDecimal.ZERO).netSalary(new BigDecimal("1000000")).build();
        when(items.findByPayrollRunIdAndEmployeeId(7L, 10L)).thenReturn(Optional.of(item));
        when(runs.findById(7L)).thenReturn(Optional.of(run));
        var byPeriod = service.myPayslipByPeriod(1L, (short) 2026, (short) 10);
        assertEquals(service.myPayslip(1L, 7L), byPeriod);
        assertEquals("Tên snapshot", byPeriod.item().employee().getFullName());
        verify(items, times(2)).findByPayrollRunIdAndEmployeeId(7L, 10L);
    }

    @Test void distinguishesMissingPeriodMissingOwnItemAndUnpublishedWithoutLoadingMoney() {
        when(runs.findByPayrollYearAndPayrollMonth((short) 2026, (short) 9)).thenReturn(Optional.empty());
        assertEquals("PAYSLIP_NOT_FOUND", assertThrows(ResourceNotFoundException.class,
                () -> service.myPayslipByPeriod(1L, (short) 2026, (short) 9)).getCode());
        for (PayrollRunStatus status : java.util.List.of(PayrollRunStatus.DRAFT, PayrollRunStatus.GENERATED, PayrollRunStatus.CANCELLED)) {
            run.setStatus(status);
            assertEquals("PAYSLIP_NOT_AVAILABLE", assertThrows(ResourceNotFoundException.class,
                    () -> service.myPayslipByPeriod(1L, (short) 2026, (short) 10)).getCode());
        }
        verifyNoInteractions(items);
        run.setStatus(PayrollRunStatus.PUBLISHED);
        assertEquals("PAYSLIP_NOT_FOUND", assertThrows(ResourceNotFoundException.class,
                () -> service.myPayslipByPeriod(1L, (short) 2026, (short) 10)).getCode());
        verify(items).findByPayrollRunIdAndEmployeeId(7L, 10L);
    }

    @Test void unknownAccountAndInvalidPeriodCannotQueryAmounts() {
        assertThrows(ResourceNotFoundException.class, () -> service.myPayslipByPeriod(2L, (short) 2026, (short) 10));
        assertEquals(400, assertThrows(AuthException.class,
                () -> service.myPayslipByPeriod(1L, (short) 2026, (short) 13)).getStatus());
        assertEquals(400, assertThrows(AuthException.class,
                () -> service.myPayslipByPeriod(1L, (short) 1999, (short) 1)).getStatus());
        verifyNoInteractions(items, runs);
    }
}
