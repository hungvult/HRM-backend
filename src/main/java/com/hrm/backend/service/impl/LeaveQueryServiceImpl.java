package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.*;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveBalance;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.LeaveType;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveBalanceRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.repository.LeaveTypeRepository;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.LeaveQueryService;
import com.hrm.backend.service.LeaveRequestReadScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveQueryServiceImpl implements LeaveQueryService {
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository leaveBalances;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;

    @Override
    public List<LeaveTypeResponse> getActiveLeaveTypes() {
        return leaveTypes.findByIsActiveTrueOrderByCodeAsc().stream().map(this::toLeaveType).toList();
    }

    @Override
    public LeaveBalanceListResponse getMyLeaveBalances(Long accountId, int year) {
        validateYear(year);
        Employee employee = currentEmployee(accountId);
        List<LeaveBalanceResponse> items = leaveBalances
                .findByEmployeeIdAndBalanceYearOrderByLeaveTypeCodeAsc(employee.getId(), (short) year)
                .stream().map(this::toBalance).toList();
        return LeaveBalanceListResponse.builder().year(year).items(items).build();
    }

    @Override
    public PagedResponse<LeaveRequestListItemResponse> getMyLeaveRequests(Long accountId,
                                                                            LeaveRequestStatus status,
                                                                            Long leaveTypeId,
                                                                            LocalDate fromDate,
                                                                            LocalDate toDate,
                                                                            int page,
                                                                            int size) {
        validateListParameters(fromDate, toDate, page, size);
        Employee employee = currentEmployee(accountId);
        return toPage(leaveRequests.findPersonalRequests(employee.getId(), status, leaveTypeId, fromDate, toDate,
                requestPage(page, size)));
    }

    @Override
    public PagedResponse<LeaveRequestListItemResponse> getScopedLeaveRequests(Long accountId,
                                                                                LeaveRequestStatus status,
                                                                                Long leaveTypeId,
                                                                                LocalDate fromDate,
                                                                                LocalDate toDate,
                                                                                int page,
                                                                                int size) {
        validateListParameters(fromDate, toDate, page, size);
        LeaveRequestReadScope scope = accessScope.resolveLeaveRequestReadScope(accountId);
        return toPage(leaveRequests.findRequestsInScope(scope.companyWide(), scope.canReadDirectReports(), scope.employeeId(), status, leaveTypeId,
                fromDate, toDate, requestPage(page, size)));
    }

    @Override
    public LeaveRequestDetailResponse getLeaveRequestDetail(Long accountId, Long leaveRequestId) {
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeById(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        // Deliberately use the same scope policy as the management list endpoint.
        accessScope.requireCanReadLeaveRequest(accountId, request.getEmployee().getId());
        return toDetail(request, statusHistories.findByLeaveRequestIdOrderByChangedAtAscIdAsc(request.getId()));
    }

    private Employee currentEmployee(Long accountId) {
        return employees.findByAccountId(accountId)
                .orElseThrow(() -> new AuthException("EMPLOYEE_PROFILE_REQUIRED", "Tài khoản chưa có hồ sơ nhân viên.", 403));
    }

    private PageRequest requestPage(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
    }

    private void validateListParameters(LocalDate fromDate, LocalDate toDate, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AuthException("VALIDATION_ERROR", "fromDate không được sau toDate.", 400);
        }
    }

    private void validateYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new AuthException("VALIDATION_ERROR", "year phải trong khoảng 2000 đến 2100.", 400);
        }
    }

    private PagedResponse<LeaveRequestListItemResponse> toPage(Page<LeaveRequest> requests) {
        return PagedResponse.<LeaveRequestListItemResponse>builder()
                .content(requests.getContent().stream().map(this::toListItem).toList())
                .page(requests.getNumber())
                .size(requests.getSize())
                .totalElements(requests.getTotalElements())
                .totalPages(requests.getTotalPages())
                .hasNext(requests.hasNext())
                .build();
    }

    private LeaveTypeResponse toLeaveType(LeaveType type) {
        return LeaveTypeResponse.builder().id(type.getId()).code(type.getCode()).name(type.getName())
                .annualQuotaDays(type.getAnnualQuotaDays()).paid(type.getIsPaid()).build();
    }

    private LeaveBalanceResponse toBalance(LeaveBalance balance) {
        return LeaveBalanceResponse.builder().leaveTypeId(balance.getLeaveType().getId())
                .leaveTypeCode(balance.getLeaveType().getCode()).leaveTypeName(balance.getLeaveType().getName())
                .allocatedDays(balance.getAllocatedDays()).usedDays(balance.getUsedDays())
                .remainingDays(balance.getAllocatedDays().subtract(balance.getUsedDays())).build();
    }

    private LeaveRequestListItemResponse toListItem(LeaveRequest request) {
        return LeaveRequestListItemResponse.builder().id(request.getId()).requestCode(request.getRequestCode())
                .employeeId(request.getEmployee().getId()).employeeCode(request.getEmployee().getEmployeeCode())
                .employeeName(request.getEmployee().getFullName()).leaveTypeId(request.getLeaveType().getId())
                .leaveTypeCode(request.getLeaveType().getCode()).leaveTypeName(request.getLeaveType().getName())
                .startDate(request.getStartDate()).endDate(request.getEndDate()).requestedDays(request.getRequestedDays())
                .status(request.getStatus().name()).createdAt(request.getCreatedAt()).build();
    }

    private LeaveRequestDetailResponse toDetail(LeaveRequest request, List<LeaveRequestStatusHistory> histories) {
        return LeaveRequestDetailResponse.builder().id(request.getId()).requestCode(request.getRequestCode())
                .employeeId(request.getEmployee().getId()).employeeCode(request.getEmployee().getEmployeeCode())
                .employeeName(request.getEmployee().getFullName()).leaveTypeId(request.getLeaveType().getId())
                .leaveTypeCode(request.getLeaveType().getCode()).leaveTypeName(request.getLeaveType().getName())
                .startDate(request.getStartDate()).endDate(request.getEndDate()).requestedDays(request.getRequestedDays())
                .reason(request.getReason()).status(request.getStatus().name())
                .reviewedByEmployeeId(request.getReviewedByEmployee() == null ? null : request.getReviewedByEmployee().getId())
                .reviewedAt(request.getReviewedAt()).rejectionReason(request.getRejectionReason())
                .cancelledAt(request.getCancelledAt()).createdAt(request.getCreatedAt()).updatedAt(request.getUpdatedAt())
                .statusHistory(histories.stream().map(this::toHistory).toList()).build();
    }

    private LeaveRequestStatusHistoryResponse toHistory(LeaveRequestStatusHistory history) {
        return LeaveRequestStatusHistoryResponse.builder()
                .oldStatus(history.getOldStatus() == null ? null : history.getOldStatus().name())
                .newStatus(history.getNewStatus().name())
                .changedByEmployeeId(history.getChangedByEmployee() == null ? null : history.getChangedByEmployee().getId())
                .changedByEmployeeName(history.getChangedByEmployee() == null ? null : history.getChangedByEmployee().getFullName())
                .changedAt(history.getChangedAt()).note(history.getNote()).build();
    }
}
