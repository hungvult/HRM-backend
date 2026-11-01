package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.*;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.*;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.AccessScopeService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UseCaseAlignmentTest {
    final DepartmentRepository departments = mock(DepartmentRepository.class);
    final EmployeeAssignmentRepository assignments = mock(EmployeeAssignmentRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final AuditLogRepository audits = mock(AuditLogRepository.class);
    final DepartmentServiceImpl catalogs = new DepartmentServiceImpl(departments, assignments, accounts, audits);

    // Vô hiệu hóa phải thành công dù còn nhân viên, nhưng không được đóng phân công cũ.
    @Test void deactivationPreservesExistingAssignments() {
        Department d = Department.builder().id(27L).code("QA27").name("QA").status(DepartmentStatus.ACTIVE).build();
        when(departments.findLiveByIdForUpdate(27L)).thenReturn(Optional.of(d));
        when(accounts.findById(1L)).thenReturn(Optional.of(Account.builder().id(1L).build()));
        when(departments.saveAndFlush(d)).thenReturn(d);
        UpdateDepartmentStatusRequest req = new UpdateDepartmentStatusRequest(); req.setStatus(DepartmentStatus.INACTIVE);
        catalogs.updateDepartmentStatus(1L, 27L, req);
        assertEquals(DepartmentStatus.INACTIVE, d.getStatus());
        assertNull(d.getDeletedAt());
        verifyNoInteractions(assignments);
        verify(audits).save(any(AuditLog.class));
    }

    @Test void deletingOccupiedCatalogStopsBeforeAnyWrite() {
        Department d = Department.builder().id(27L).status(DepartmentStatus.ACTIVE).build();
        when(departments.findLiveByIdForUpdate(27L)).thenReturn(Optional.of(d));
        when(assignments.existsByDepartmentIdAndIsCurrentTrue(27L)).thenReturn(true);
        assertEquals("DEPARTMENT_HAS_ACTIVE_ASSIGNMENTS", assertThrows(AuthException.class,
                () -> catalogs.deleteDepartment(1L, 27L)).getCode());
        assertNull(d.getDeletedAt());
        verify(departments, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test void deletingEmptyCatalogKeepsTheHistoricalEntity() {
        Department d = Department.builder().id(27L).code("QA27").name("QA").status(DepartmentStatus.ACTIVE).build();
        when(departments.findLiveByIdForUpdate(27L)).thenReturn(Optional.of(d));
        catalogs.deleteDepartment(1L, 27L);
        assertNotNull(d.getDeletedAt());
        assertEquals(27L, d.getId());
        assertEquals("QA27", d.getCode());
        assertEquals(DepartmentStatus.INACTIVE, d.getStatus());
        verify(departments, never()).delete(any());
        verify(audits).save(any(AuditLog.class));
    }

    // Cùng ngày phải giữ cả bản cũ và mới, không tạo khoảng kết thúc trước bắt đầu.
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void repeatedSameDayAssignmentHasValidHistory(boolean oldCatalogsMissing) {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        PositionRepository positions = mock(PositionRepository.class);
        Employee e = Employee.builder().id(116L).employmentStatus(EmploymentStatus.WORKING).build();
        Department d = Department.builder().id(1L).status(DepartmentStatus.ACTIVE).build();
        Position p = Position.builder().id(2L).status(PositionStatus.ACTIVE).build();
        Account actor = Account.builder().id(1L).build();
        EmployeeAssignment previous = EmployeeAssignment.builder().id(74L).employee(e)
                .department(oldCatalogsMissing ? null : d).position(oldCatalogsMissing ? null : p)
                .effectiveFrom(LocalDate.now()).isCurrent(true).assignedByAccount(actor).build();
        when(employees.findByIdForUpdate(116L)).thenReturn(Optional.of(e));
        when(departments.findLiveByIdForUpdate(1L)).thenReturn(Optional.of(d));
        when(positions.findLiveByIdForUpdate(2L)).thenReturn(Optional.of(p));
        when(accounts.findById(1L)).thenReturn(Optional.of(actor));
        when(assignments.findCurrentAssignmentForUpdate(116L)).thenReturn(Optional.of(previous));
        when(assignments.saveAndFlush(any())).thenAnswer(call -> { EmployeeAssignment a=call.getArgument(0);a.setId(75L);return a; });
        var service = new EmployeeAssignmentServiceImpl(assignments, employees, departments, positions, accounts, audits, mock(AccessScopeService.class));
        var req = new CreateEmployeeAssignmentRequest(); req.setEmployeeId(116L);req.setDepartmentId(1L);req.setPositionId(2L);
        var result=service.createAssignment(1L, req);
        assertEquals(LocalDate.now(), previous.getEffectiveTo());
        assertFalse(previous.getIsCurrent());
        assertEquals(75L, result.getId());
        assertTrue(result.getIsCurrent());
    }
}
