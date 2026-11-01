package com.hrm.backend.service.impl;

import com.hrm.backend.dto.response.LeaveRequestDetailResponse;
import com.hrm.backend.entity.Employee;
import com.hrm.backend.entity.LeaveRequest;
import com.hrm.backend.entity.LeaveRequestStatusHistory;
import com.hrm.backend.entity.enums.LeaveRequestStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.EmployeeRepository;
import com.hrm.backend.repository.LeaveRequestRepository;
import com.hrm.backend.repository.LeaveRequestStatusHistoryRepository;
import com.hrm.backend.service.LeaveQueryService;
import com.hrm.backend.service.LeaveRequestCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LeaveRequestCancellationServiceImpl implements LeaveRequestCancellationService {
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveRequestStatusHistoryRepository statusHistories;
    private final LeaveQueryService leaveQueryService;

    @Override
    @Transactional
    // Giữ caller cũ không gửi lý do; vẫn dùng cùng khóa đơn và transaction với luồng mới.
    public LeaveRequestDetailResponse cancel(Long accountId, Long leaveRequestId) {
        return cancel(accountId, leaveRequestId, null);
    }

    @Override
    @Transactional
    // accountId là người đang đăng nhập, leaveRequestId là ID đơn. Chỉ chủ đơn được hủy khi còn PENDING.
    // Khóa đơn dùng chung với duyệt: lượt sau thấy trạng thái đã đổi sẽ trả 409, không ghi lịch sử trái nhau.
    // Ghi CANCELLED/cancelled_at và reason vào history.note cùng transaction; không sửa used_days.
    public LeaveRequestDetailResponse cancel(Long accountId, Long leaveRequestId, String reason) {
        // Kiểm tra cả ở service để caller ngoài HTTP không bỏ qua giới hạn; chuỗi trắng được coi là không nhập.
        if (reason != null && reason.length() > 1000) {
            throw new AuthException("VALIDATION_ERROR", "Lý do hủy không được quá 1.000 ký tự.", 400);
        }
        String note = reason == null || reason.isBlank() ? null : reason.trim();
        Employee actor = employees.findByAccountId(accountId)
                .orElseThrow(() -> new AuthException("EMPLOYEE_PROFILE_REQUIRED", "Tài khoản chưa có hồ sơ nhân viên.", 403));
        LeaveRequest request = leaveRequests.findWithEmployeeAndLeaveTypeByIdForUpdate(leaveRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("LEAVE_REQUEST_NOT_FOUND", "Không tìm thấy đơn nghỉ phép."));
        if (!request.getEmployee().getId().equals(actor.getId())) {
            throw new AuthException("LEAVE_REQUEST_NOT_OWNER", "Bạn chỉ được hủy đơn nghỉ phép của chính mình.", 403);
        }
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw new AuthException("LEAVE_REQUEST_NOT_CANCELLABLE", "Chỉ có thể hủy đơn nghỉ phép đang chờ xử lý.", 409);
        }

        // Chỉ ghi sau khi xác nhận chủ đơn và trạng thái dưới khóa; giao dịch lỗi sẽ giữ nguyên PENDING.
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCancelledAt(OffsetDateTime.now());
        statusHistories.save(LeaveRequestStatusHistory.builder().leaveRequest(request)
                .oldStatus(LeaveRequestStatus.PENDING).newStatus(LeaveRequestStatus.CANCELLED)
                .changedByEmployee(actor).note(note).build());

        // Quỹ giữ chỗ dựa trên PENDING: hủy thì không còn bị tính, không cộng/hoàn used_days cho phép không lương.
        return leaveQueryService.getLeaveRequestDetail(accountId, leaveRequestId);
    }
}
