package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceAdjustmentHistoryResponse;
import com.hrm.backend.dto.response.AttendanceManagementListItemResponse;
import com.hrm.backend.dto.response.AttendanceMutationResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.request.AdjustAttendanceRequest;
import com.hrm.backend.dto.request.CreateAttendanceRequest;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AttendanceAdjustmentHistory;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.EmployeeAssignment;
import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.mapper.AttendanceMapper;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AttendanceAdjustmentHistoryRepository;
import com.hrm.backend.repository.AttendanceRecordRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.AttendanceManagementService;
import com.hrm.backend.service.AttendanceTimeCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceManagementServiceImpl implements AttendanceManagementService {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("workDate", "checkInAt", "checkOutAt", "workingMinutes", "attendanceStatus");

    private final AttendanceRecordRepository attendanceRecords;
    private final AttendanceAdjustmentHistoryRepository adjustmentHistories;
    private final EmployeeRepository employees;
    private final AccountRepository accounts;
    private final EmployeeAssignmentRepository assignments;
    private final AccessScopeService accessScope;
    private final AttendanceTimeCalculator timeCalculator;
    private final AttendanceMapper attendanceMapper;

    @Override
    public PagedResponse<AttendanceManagementListItemResponse> searchAttendances(Long actorAccountId, String q, Long employeeId,
                                                                                   Long departmentId, AttendanceStatus status,
                                                                                   LocalDate fromDate, LocalDate toDate, int page,
                                                                                   int size, String sortBy, String sortDirection) {
        validateSearch(fromDate, toDate, page, size, sortBy, sortDirection);
        Long managerEmployeeId = accessScope.managerEmployeeIdForAttendanceScope(actorAccountId);
        Page<AttendanceRecord> result = attendanceRecords.searchAttendances(
                q == null ? "" : q.trim(), employeeId, departmentId, status, fromDate, toDate, managerEmployeeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy)));
        Map<Long, List<EmployeeAssignment>> assignmentsByEmployee = assignmentsByEmployee(result.getContent());

        return PagedResponse.<AttendanceManagementListItemResponse>builder()
                .content(result.getContent().stream()
                        .map(record -> attendanceMapper.toListItem(record,
                                assignmentAt(record, assignmentsByEmployee.get(record.getEmployee().getId()))))
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
        AttendanceRecord record = attendanceRecords.findDetailById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTENDANCE_NOT_FOUND", "Không tìm thấy bản ghi công."));
        accessScope.requireCanReadEmployeeAtDate(actorAccountId, record.getEmployee().getId(), record.getWorkDate());
        return attendanceMapper.toDetail(record,
                assignmentAt(record, assignmentsByEmployee(List.of(record)).get(record.getEmployee().getId())));
    }

    @Override
    @Transactional
    public AttendanceMutationResponse createAttendance(Long actorAccountId, CreateAttendanceRequest request) {
        accessScope.requireCanReadEmployeeAtDate(actorAccountId, request.getEmployeeId(), request.getWorkDate());
        Employee employee = employees.findByIdForUpdate(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        if (attendanceRecords.findByEmployeeIdAndWorkDateForUpdate(employee.getId(), request.getWorkDate()).isPresent()) {
            throw conflict("ATTENDANCE_ALREADY_EXISTS", "Nhân viên đã có bản ghi công trong ngày này.");
        }
        Account actor = requireActor(actorAccountId);
        Integer workingMinutes = timeCalculator.calculateWorkingMinutes(
                request.getWorkDate(), request.getCheckInAt(), request.getCheckOutAt(), request.getAttendanceStatus());
        AttendanceRecord record = attendanceRecords.save(AttendanceRecord.builder()
                .employee(employee)
                .workDate(request.getWorkDate())
                .checkInAt(request.getCheckInAt())
                .checkOutAt(request.getCheckOutAt())
                .workingMinutes(workingMinutes)
                .attendanceStatus(request.getAttendanceStatus())
                .isManuallyAdjusted(true)
                .adjustmentReason(normalizeReason(request.getReason()))
                .adjustedByAccount(actor)
                .build());
        adjustmentHistories.save(newHistory(record, null, actor, record.getAdjustmentReason()));
        return mutationResponse(record);
    }

    @Override
    @Transactional
    public AttendanceMutationResponse adjustAttendance(Long actorAccountId, Long attendanceId, AdjustAttendanceRequest request) {
        AttendanceRecord record = attendanceRecords.findByIdForUpdate(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTENDANCE_NOT_FOUND", "Không tìm thấy bản ghi công."));
        accessScope.requireCanReadEmployeeAtDate(actorAccountId, record.getEmployee().getId(), record.getWorkDate());
        Account actor = requireActor(actorAccountId);
        AttendanceAdjustmentHistory previous = snapshot(record);
        Integer workingMinutes = timeCalculator.calculateWorkingMinutes(
                record.getWorkDate(), request.getCheckInAt(), request.getCheckOutAt(), request.getAttendanceStatus());
        record.setCheckInAt(request.getCheckInAt());
        record.setCheckOutAt(request.getCheckOutAt());
        record.setWorkingMinutes(workingMinutes);
        record.setAttendanceStatus(request.getAttendanceStatus());
        record.setIsManuallyAdjusted(true);
        record.setAdjustmentReason(normalizeReason(request.getReason()));
        record.setAdjustedByAccount(actor);
        AttendanceRecord saved = attendanceRecords.save(record);
        adjustmentHistories.save(newHistory(saved, previous, actor, saved.getAdjustmentReason()));
        return mutationResponse(saved);
    }

    @Override
    public PagedResponse<AttendanceAdjustmentHistoryResponse> getAdjustmentHistory(Long actorAccountId, Long attendanceId,
                                                                                      int page, int size) {
        validatePage(page, size);
        AttendanceRecord record = attendanceRecords.findDetailById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("ATTENDANCE_NOT_FOUND", "Không tìm thấy bản ghi công."));
        accessScope.requireCanReadEmployeeAtDate(actorAccountId, record.getEmployee().getId(), record.getWorkDate());
        Page<AttendanceAdjustmentHistory> result = adjustmentHistories
                .findByAttendanceRecordIdOrderByAdjustedAtDesc(attendanceId, PageRequest.of(page, size));
        return PagedResponse.<AttendanceAdjustmentHistoryResponse>builder()
                .content(result.getContent().stream().map(this::historyResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    private Map<Long, List<EmployeeAssignment>> assignmentsByEmployee(Collection<AttendanceRecord> records) {
        if (records.isEmpty()) return Map.of();
        LocalDate fromDate = records.stream().map(AttendanceRecord::getWorkDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate toDate = records.stream().map(AttendanceRecord::getWorkDate).max(Comparator.naturalOrder()).orElseThrow();
        Set<Long> employeeIds = records.stream().map(record -> record.getEmployee().getId()).collect(Collectors.toSet());
        return assignments.findEffectiveAssignmentsForEmployeesBetween(employeeIds, fromDate, toDate).stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getEmployee().getId()));
    }

    private EmployeeAssignment assignmentAt(AttendanceRecord record, List<EmployeeAssignment> employeeAssignments) {
        if (employeeAssignments == null) return null;
        return employeeAssignments.stream()
                .filter(assignment -> !assignment.getEffectiveFrom().isAfter(record.getWorkDate()))
                .filter(assignment -> assignment.getEffectiveTo() == null || !assignment.getEffectiveTo().isBefore(record.getWorkDate()))
                .max(Comparator.comparing(EmployeeAssignment::getEffectiveFrom))
                .orElse(null);
    }

    private void validateSearch(LocalDate fromDate, LocalDate toDate, int page, int size, String sortBy, String sortDirection) {
        validatePage(page, size);
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

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
    }

    private Account requireActor(Long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
    }

    private String normalizeReason(String reason) {
        return reason.trim();
    }

    private AttendanceAdjustmentHistory snapshot(AttendanceRecord record) {
        return AttendanceAdjustmentHistory.builder()
                .previousCheckInAt(record.getCheckInAt())
                .previousCheckOutAt(record.getCheckOutAt())
                .previousWorkingMinutes(record.getWorkingMinutes())
                .previousAttendanceStatus(record.getAttendanceStatus())
                .build();
    }

    private AttendanceAdjustmentHistory newHistory(AttendanceRecord record, AttendanceAdjustmentHistory previous,
                                                    Account actor, String reason) {
        return AttendanceAdjustmentHistory.builder()
                .attendanceRecord(record)
                .previousCheckInAt(previous == null ? null : previous.getPreviousCheckInAt())
                .previousCheckOutAt(previous == null ? null : previous.getPreviousCheckOutAt())
                .previousWorkingMinutes(previous == null ? null : previous.getPreviousWorkingMinutes())
                .previousAttendanceStatus(previous == null ? null : previous.getPreviousAttendanceStatus())
                .newCheckInAt(record.getCheckInAt())
                .newCheckOutAt(record.getCheckOutAt())
                .newWorkingMinutes(record.getWorkingMinutes())
                .newAttendanceStatus(record.getAttendanceStatus())
                .adjustmentReason(reason)
                .adjustedByAccount(actor)
                .build();
    }

    private AttendanceMutationResponse mutationResponse(AttendanceRecord record) {
        return AttendanceMutationResponse.builder()
                .id(record.getId())
                .employeeId(record.getEmployee().getId())
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workingMinutes(record.getWorkingMinutes())
                .attendanceStatus(record.getAttendanceStatus())
                .manuallyAdjusted(record.getIsManuallyAdjusted())
                .adjustmentReason(record.getAdjustmentReason())
                .build();
    }

    private AttendanceAdjustmentHistoryResponse historyResponse(AttendanceAdjustmentHistory history) {
        return AttendanceAdjustmentHistoryResponse.builder()
                .id(history.getId())
                .previousCheckInAt(history.getPreviousCheckInAt())
                .previousCheckOutAt(history.getPreviousCheckOutAt())
                .previousWorkingMinutes(history.getPreviousWorkingMinutes())
                .previousAttendanceStatus(history.getPreviousAttendanceStatus())
                .newCheckInAt(history.getNewCheckInAt())
                .newCheckOutAt(history.getNewCheckOutAt())
                .newWorkingMinutes(history.getNewWorkingMinutes())
                .newAttendanceStatus(history.getNewAttendanceStatus())
                .reason(history.getAdjustmentReason())
                .adjustedByAccountId(history.getAdjustedByAccount().getId())
                .adjustedByUsername(history.getAdjustedByAccount().getUsername())
                .adjustedAt(history.getAdjustedAt())
                .build();
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
