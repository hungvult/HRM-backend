package com.hrm.backend.service.impl;

import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.PayrollItem;
import com.hrm.backend.entity.PayrollRun;
import com.hrm.backend.entity.enums.PayrollRunStatus;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.PayrollItemViewRepository;
import com.hrm.backend.repository.PayrollRunViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollViewServiceImplTest {
    private final PayrollRunViewRepository runs = mock(PayrollRunViewRepository.class);
    private final PayrollItemViewRepository items = mock(PayrollItemViewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PayrollViewServiceImpl service = new PayrollViewServiceImpl(runs, items, employees);

    @Test
    void runOverviewUsesSavedItemTotalsInsteadOfLivePayrollInputs() {
        PayrollRun run = run(88L);
        PayrollItemViewRepository.Totals totals = totals(2L, "43000000", "4300000", "2150000", "45150000");
        when(runs.findById(88L)).thenReturn(Optional.of(run));
        when(items.summarizeByRunId(88L)).thenReturn(totals);

        var response = service.getRun(88L);

        assertEquals(88L, response.id());
        assertEquals(2026, response.period().year().intValue());
        assertEquals(22, response.workStandard().standardWorkingDays().intValue());
        assertEquals(new BigDecimal("45150000"), response.summary().totalNetSalary());
        assertEquals(2, response.summary().employeeCount());
    }

    @Test
    void runItemsReturnStoredSnapshotAndTotalsForTheWholeFilteredSet() {
        PayrollRun run = run(88L);
        PayrollItem item = PayrollItem.builder().id(501L).payrollRun(run)
                .employee(Employee.builder().id(10L).employeeCode("CHANGED").fullName("Changed name").build())
                .employeeCodeSnapshot("NV00010").employeeFullNameSnapshot("Nguyễn An")
                .departmentIdSnapshot(3L).departmentCodeSnapshot("IT").departmentNameSnapshot("Công nghệ")
                .paidMinutes(10_320).paidWorkingDays(new BigDecimal("21.5000"))
                .baseSalarySnapshot(new BigDecimal("22000000")).allowanceSnapshot(new BigDecimal("2200000"))
                .deductionSnapshot(new BigDecimal("1100000")).earnedBaseSalary(new BigDecimal("21500000"))
                .allowanceAmount(new BigDecimal("2150000")).deductionAmount(new BigDecimal("1075000"))
                .netSalary(new BigDecimal("22575000")).build();
        PayrollItemViewRepository.Totals totals = totals(2L, "43000000", "4300000", "2150000", "45150000");
        @SuppressWarnings("unchecked")
        Page<PayrollItem> page = mock(Page.class);
        when(runs.findById(88L)).thenReturn(Optional.of(run));
        when(items.findPageByRunSnapshotFilters(eq(88L), eq("nguyễn"), eq(3L), any()))
                .thenReturn(page);
        when(items.summarizeByRunSnapshotFilters(88L, "nguyễn", 3L)).thenReturn(totals);
        when(page.getContent()).thenReturn(List.of(item));
        when(page.getNumber()).thenReturn(0);
        when(page.getSize()).thenReturn(20);
        when(page.getTotalElements()).thenReturn(2L);
        when(page.getTotalPages()).thenReturn(1);
        when(page.hasNext()).thenReturn(false);

        var response = service.getRunItems(88L, "  nguyễn  ", 3L, 0, 20);

        assertEquals("NV00010", response.content().getFirst().employee().getEmployeeCode());
        assertEquals("Nguyễn An", response.content().getFirst().employee().getFullName());
        assertEquals("Công nghệ", response.content().getFirst().employee().getDepartment().getName());
        assertEquals(new BigDecimal("22575000"), response.content().getFirst().amounts().netSalary());
        assertEquals(2, response.filteredSummary().employeeCount());
        assertEquals(new BigDecimal("45150000"), response.filteredSummary().totalNetSalary());
        assertEquals(2, response.totalElements());
        verify(items).findPageByRunSnapshotFilters(eq(88L), eq("nguyễn"), eq(3L),
                eq(PageRequest.of(0, 20, Sort.by("employeeCodeSnapshot").ascending()
                        .and(Sort.by("employee.id").ascending()))));
    }

    @Test
    void accountWithoutEmployeeReceivesAnEmptyPayslipList() {
        when(employees.findByAccountId(77L)).thenReturn(Optional.empty());

        var response = service.getMyPayslips(77L, null, 0, 20);

        assertEquals(0, response.getTotalElements());
        assertEquals(List.of(), response.getContent());
        verify(items, never()).findPublishedByEmployee(any(), any(), any(), any());
    }

    @Test
    void runListUsesSavedTotalsAndStableNewestFirstSort() {
        PayrollRun run = run(88L);
        PayrollItemViewRepository.RunTotals totals = mock(PayrollItemViewRepository.RunTotals.class);
        when(totals.getRunId()).thenReturn(88L);
        when(totals.getEmployeeCount()).thenReturn(2L);
        when(totals.getTotalNetSalary()).thenReturn(new BigDecimal("45150000"));
        when(runs.findPageByFilters(eq((short) 2026), eq((short) 9), eq(PayrollRunStatus.GENERATED),
                eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1));
        when(items.summarizeByRunIds(List.of(88L))).thenReturn(List.of(totals));

        var response = service.getRuns((short) 2026, (short) 9, PayrollRunStatus.GENERATED,
                null, null, null, null, 0, 20);

        assertEquals(88L, response.getContent().getFirst().id());
        assertEquals(2L, response.getContent().getFirst().employeeCount());
        assertEquals(new BigDecimal("45150000"), response.getContent().getFirst().totalNetSalary());
        verify(runs).findPageByFilters(eq((short) 2026), eq((short) 9), eq(PayrollRunStatus.GENERATED),
                eq(null), eq(null), eq(null), eq(null),
                eq(PageRequest.of(0, 20, Sort.by("payrollYear").descending().and(Sort.by("payrollMonth").descending())
                        .and(Sort.by("id").descending()))));
    }

    @Test
    void runListAcceptsOneSidedPeriodRangeAndPreservesEmptyPageMetadata() {
        PageRequest requestedPage = PageRequest.of(5, 20, Sort.by("payrollYear").descending()
                .and(Sort.by("payrollMonth").descending()).and(Sort.by("id").descending()));
        when(runs.findPageByFilters(null, null, null, (short) 2026, (short) 9, null, null, requestedPage))
                .thenReturn(new PageImpl<>(List.of(), requestedPage, 100));

        var response = service.getRuns(null, null, null, (short) 2026, (short) 9,
                null, null, 5, 20);

        assertEquals(List.of(), response.getContent());
        assertEquals(5, response.getPage());
        assertEquals(20, response.getSize());
        assertEquals(100, response.getTotalElements());
        assertEquals(5, response.getTotalPages());
        verify(items, never()).summarizeByRunIds(any());
    }

    private PayrollRun run(Long id) {
        return PayrollRun.builder().id(id).payrollYear((short) 2026).payrollMonth((short) 9)
                .status(PayrollRunStatus.GENERATED).standardWorkingDays(22).standardMinutesPerDay(480).build();
    }

    private PayrollItemViewRepository.Totals totals(long employeeCount, String earnedBase, String allowance,
                                                     String deduction, String net) {
        PayrollItemViewRepository.Totals totals = mock(PayrollItemViewRepository.Totals.class);
        when(totals.getEmployeeCount()).thenReturn(employeeCount);
        when(totals.getTotalEarnedBaseSalary()).thenReturn(new BigDecimal(earnedBase));
        when(totals.getTotalEarnedAllowance()).thenReturn(new BigDecimal(allowance));
        when(totals.getTotalEarnedDeduction()).thenReturn(new BigDecimal(deduction));
        when(totals.getTotalNetSalary()).thenReturn(new BigDecimal(net));
        return totals;
    }
}
