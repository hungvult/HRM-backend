package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceListItemResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.AccountRole;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.mapper.AttendanceMapper;
import com.hrm.backend.repository.AccountRoleRepository;
import com.hrm.backend.repository.AttendanceRecordRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.AttendanceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceManagementServiceImpl implements AttendanceManagementService {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("workDate", "checkInAt", "checkOutAt", "workingMinutes", "attendanceStatus");

    private final AttendanceRecordRepository attendanceRecords;
    private final EmployeeRepository employees;
    private final EmployeeAssignmentRepository assignments;
    private final AccountRoleRepository accountRoles;
    private final AttendanceMapper attendanceMapper;

    @Override
    public PagedResponse<AttendanceListItemResponse> searchAttendances(Long actorAccountId, String q, Long employeeId,
                                                                         Long departmentId, AttendanceStatus status,
                                                                         LocalDate fromDate, LocalDate toDate, int page,
                                                                         int size, String sortBy, String sortDirection) {
        validateSearch(fromDate, toDate, page, size, sortBy, sortDirection);
        Long managerEmployeeId = managerScopeEmployeeId(actorAccountId);
        Page<AttendanceRecord> result = attendanceRecords.searchAttendances(
                q == null ? "" : q.trim(), employeeId, departmentId, status, fromDate, toDate, managerEmployeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy)));

        return PagedResponse.<AttendanceListItemResponse>builder()
                .content(result.getContent().stream()
                        .map(record -> attendanceMapper.toListItem(record,
                                assignments.findCurrentAssignmentByEmployeeId(record.getEmployee().getId()).orElse(null)))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Override
    public AttendanceDetailResponse getAttendance(Long actorAccountId, Long attendanceId) {
        AttendanceRecord record = attendanceRecords.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTENDANCE_NOT_FOUND", "Không tìm thấy bản ghi công."));
        requireCanReadAttendance(actorAccountId, record);
        return attendanceMapper.toDetail(record,
                assignments.findCurrentAssignmentByEmployeeId(record.getEmployee().getId()).orElse(null));
    }

    private Long managerScopeEmployeeId(Long accountId) {
        Set<String> roles = rolesFor(accountId);
        if (roles.contains("ADMIN") || roles.contains("HR")) {
            return null;
        }
        if (!roles.contains("MANAGER")) {
            throw forbidden("Bạn không có quyền xem bảng công.");
        }
        return findManagerEmployee(accountId).getId();
    }

    private void requireCanReadAttendance(Long accountId, AttendanceRecord record) {
        Set<String> roles = rolesFor(accountId);
        if (roles.contains("ADMIN") || roles.contains("HR")) {
            return;
        }
        if (!roles.contains("MANAGER")) {
            throw forbidden("Bạn không có quyền xem bản ghi công này.");
        }
        Employee manager = findManagerEmployee(accountId);
        if (assignments.countManagedEmployeeAtDate(record.getEmployee().getId(), manager.getId(), record.getWorkDate()) == 0) {
            throw forbidden("Bạn không có quyền xem bản ghi công này.");
        }
    }

    private Set<String> rolesFor(Long accountId) {
        return accountRoles.findByAccountIdWithRole(accountId).stream()
                .map(AccountRole::getRole)
                .map(role -> role.getCode())
                .collect(Collectors.toSet());
    }

    private Employee findManagerEmployee(Long accountId) {
        return employees.findByAccountId(accountId)
                .orElseThrow(() -> forbidden("Tài khoản quản lý không có hồ sơ nhân viên."));
    }

    private void validateSearch(LocalDate fromDate, LocalDate toDate, int page, int size, String sortBy, String sortDirection) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AuthException("VALIDATION_ERROR", "fromDate không được sau toDate.", 400);
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new AuthException("VALIDATION_ERROR", "sortBy không hợp lệ.", 400);
        }
        try {
            Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new AuthException("VALIDATION_ERROR", "sortDirection chỉ nhận asc hoặc desc.", 400);
        }
    }

    private AuthException forbidden(String message) {
        return new AuthException("AUTH_FORBIDDEN_SCOPE", message, 403);
    }
}
