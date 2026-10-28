package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreateLeaveRequest;
import com.hrm.backend.dto.response.LeaveRequestSubmissionResponse;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveBalance;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.LeaveType;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveBalanceRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.repository.LeaveTypeRepository;
import com.hrm.backend.service.LeaveDurationCalculator;
import com.hrm.backend.service.LeaveRequestNotificationService;
import com.hrm.backend.service.LeaveRequestSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveRequestSubmissionServiceImpl implements LeaveRequestSubmissionService {
    private static final Set<LeaveRequestStatus> OVERLAP_STATUSES = Set.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);
    private final EmployeeRepository employees;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository leaveBalances;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final LeaveDurationCalculator durationCalculator;
    private final LeaveRequestNotificationService notificationService;

    @Value("${app.company-timezone:Asia/Ho_Chi_Minh}")
    private String companyTimezone;

    @Override
    @Transactional
    public LeaveRequestSubmissionResponse submit(Long accountId, CreateLeaveRequest request) {
        Employee employee = employees.findByAccountId(accountId)
                .flatMap(found -> employees.findByIdForUpdate(found.getId()))
                .orElseThrow(() -> forbidden("Tài khoản chưa có hồ sơ nhân viên."));
        validateDates(request.getStartDate(), request.getEndDate());
        LeaveType leaveType = leaveTypes.findById(request.getLeaveTypeId())
                .filter(type -> Boolean.TRUE.equals(type.getIsActive()))
                .orElseThrow(() -> new AuthException("LEAVE_TYPE_NOT_AVAILABLE", "Loại phép không tồn tại hoặc đã ngừng hiệu lực.", 400));
        if (leaveRequests.existsOverlappingRequest(employee.getId(), OVERLAP_STATUSES,
                request.getStartDate(), request.getEndDate())) {
            throw new AuthException("LEAVE_REQUEST_OVERLAP", "Khoảng ngày nghỉ bị chồng lấn với đơn đang chờ duyệt hoặc đã được duyệt.", 409);
        }

        BigDecimal requestedDays = durationCalculator.calculate(request.getStartDate(), request.getEndDate());
        if (requestedDays.signum() <= 0) {
            throw new AuthException("LEAVE_REQUEST_NO_WORKING_DAY", "Khoảng thời gian đăng ký không có ngày làm việc.", 400);
        }
        ensureSufficientBalance(employee, leaveType, request.getStartDate(), requestedDays);

        LeaveRequest leaveRequest = leaveRequests.saveAndFlush(LeaveRequest.builder()
                .requestCode(temporaryRequestCode()).employee(employee).leaveType(leaveType)
                .startDate(request.getStartDate()).endDate(request.getEndDate()).requestedDays(requestedDays)
                .reason(request.getReason().trim()).status(LeaveRequestStatus.PENDING).build());
        leaveRequest.setRequestCode(String.format("NP%06d", leaveRequest.getId()));
        leaveRequests.save(leaveRequest);
        statusHistories.save(LeaveRequestStatusHistory.builder().leaveRequest(leaveRequest)
                .newStatus(LeaveRequestStatus.PENDING).changedByEmployee(employee).build());
        notificationService.notifySubmitted(leaveRequest);
        return LeaveRequestSubmissionResponse.builder().id(leaveRequest.getId()).requestCode(leaveRequest.getRequestCode())
                .leaveTypeId(leaveType.getId()).startDate(leaveRequest.getStartDate()).endDate(leaveRequest.getEndDate())
                .requestedDays(requestedDays).status(LeaveRequestStatus.PENDING.name()).createdAt(leaveRequest.getCreatedAt()).build();
    }

    private void ensureSufficientBalance(Employee employee, LeaveType leaveType, LocalDate startDate, BigDecimal requestedDays) {
        LeaveBalance balance = leaveBalances.findForUpdate(employee.getId(), leaveType.getId(), (short) startDate.getYear())
                .orElseThrow(() -> new AuthException("LEAVE_BALANCE_NOT_FOUND", "Chưa có số dư phép cho loại phép và năm đã chọn.", 400));
        LocalDate yearStart = startDate.withDayOfYear(1);
        LocalDate yearEnd = startDate.withMonth(12).withDayOfMonth(31);
        BigDecimal pendingDays = leaveRequests.sumRequestedDays(employee.getId(), leaveType.getId(), LeaveRequestStatus.PENDING,
                yearStart, yearEnd);
        BigDecimal availableDays = balance.getAllocatedDays().subtract(balance.getUsedDays()).subtract(pendingDays);
        if (availableDays.compareTo(requestedDays) < 0) {
            throw new AuthException("LEAVE_BALANCE_INSUFFICIENT", "Số dư phép còn lại không đủ để gửi đơn này.", 400);
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(ZoneId.of(companyTimezone));
        if (startDate.isBefore(today)) {
            throw new AuthException("LEAVE_REQUEST_PAST_DATE", "Không thể gửi đơn nghỉ phép cho ngày trong quá khứ.", 400);
        }
        if (endDate.isBefore(startDate)) {
            throw new AuthException("LEAVE_REQUEST_DATE_RANGE_INVALID", "Ngày kết thúc nghỉ không được trước ngày bắt đầu nghỉ.", 400);
        }
        if (startDate.getYear() != endDate.getYear()) {
            throw new AuthException("LEAVE_REQUEST_CROSS_YEAR_UNSUPPORTED", "Đơn nghỉ phép chỉ được đăng ký trong cùng một năm.", 400);
        }
    }

    private String temporaryRequestCode() {
        return "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private AuthException forbidden(String message) {
        return new AuthException("EMPLOYEE_PROFILE_REQUIRED", message, 403);
    }
}
