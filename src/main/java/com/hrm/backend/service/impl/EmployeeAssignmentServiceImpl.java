package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreateEmployeeAssignmentRequest;
import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.dto.response.EmployeeAssignmentResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PositionDto;
import com.hrm.backend.entity.*;
import com.hrm.backend.entity.enums.DepartmentStatus;
import com.hrm.backend.entity.enums.EmploymentStatus;
import com.hrm.backend.entity.enums.PositionStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.*;
import com.hrm.backend.service.AccessScopeService;
import com.hrm.backend.service.EmployeeAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAssignmentServiceImpl implements EmployeeAssignmentService {
    private final EmployeeAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;
    private final AccessScopeService accessScope;

    /** HR phân công employees.id vào departments.id/positions.id; managerEmployeeId cũng là employees.id.
     * Khóa hồ sơ và danh mục, kiểm đang hiệu lực/tự quản lý trước khi đóng phân công hiện tại và tạo bản mới.
     * Cùng ngày giữ khoảng ngày hợp lệ, bản ID cuối quyết định hiệu lực; audit ghi trước/sau và transaction rollback khi lỗi. */
    @Override
    @Transactional
    public EmployeeAssignmentResponse createAssignment(Long actorAccountId, CreateEmployeeAssignmentRequest request) {
        LocalDate effectiveFrom = request.getEffectiveFrom() == null ? LocalDate.now() : request.getEffectiveFrom();
        if (!effectiveFrom.equals(LocalDate.now())) {
            throw badRequest("ASSIGNMENT_EFFECTIVE_DATE_INVALID", "Ngày hiệu lực phải là ngày hiện tại.");
        }

        // Khóa hồ sơ nhân viên để hai yêu cầu đồng thời không tạo hai phân công hiện hành.
        Employee employee = employees.findByIdForUpdate(request.getEmployeeId())
                .orElseThrow(() -> notFound("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên."));
        requireWorking(employee, "Nhân viên được phân công phải đang làm việc.");

        Department department = departments.findLiveByIdForUpdate(request.getDepartmentId())
                .orElseThrow(() -> notFound("DEPARTMENT_NOT_FOUND", "Không tìm thấy phòng ban."));
        if (department.getStatus() != DepartmentStatus.ACTIVE) {
            throw conflict("DEPARTMENT_INACTIVE", "Không thể phân công vào phòng ban không còn hiệu lực.");
        }
        Position position = positions.findLiveByIdForUpdate(request.getPositionId())
                .orElseThrow(() -> notFound("POSITION_NOT_FOUND", "Không tìm thấy chức vụ."));
        if (position.getStatus() != PositionStatus.ACTIVE) {
            throw conflict("POSITION_INACTIVE", "Không thể phân công vào chức vụ không còn hiệu lực.");
        }

        Employee manager = null;
        if (request.getManagerEmployeeId() != null) {
            if (request.getManagerEmployeeId().equals(employee.getId())) {
                throw badRequest("ASSIGNMENT_SELF_MANAGER", "Nhân viên không thể là quản lý trực tiếp của chính mình.");
            }
            manager = employees.findById(request.getManagerEmployeeId())
                    .orElseThrow(() -> notFound("MANAGER_EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên quản lý trực tiếp."));
            requireWorking(manager, "Quản lý trực tiếp phải đang làm việc.");
            if (assignments.existsByEmployeeIdAndManagerEmployeeIdAndIsCurrentTrue(manager.getId(), employee.getId())) {
                throw badRequest("ASSIGNMENT_MANAGER_CYCLE", "Không thể tạo vòng lặp quản lý trực tiếp.");
            }
        }

        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> notFound("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        EmployeeAssignment current = assignments.findCurrentAssignmentForUpdate(employee.getId()).orElse(null);
        String oldData = current == null ? null : assignmentAuditData(current);
        if (current != null) {
            current.setIsCurrent(false);
            // Cùng ngày giữ bản cũ trong lịch sử; ID lớn hơn quyết định bản hiệu lực cuối ngày.
            current.setEffectiveTo(current.getEffectiveFrom().equals(effectiveFrom) ? effectiveFrom : effectiveFrom.minusDays(1));
            assignments.save(current);
        }

        EmployeeAssignment saved = assignments.saveAndFlush(EmployeeAssignment.builder()
                .employee(employee).department(department).position(position).managerEmployee(manager)
                .effectiveFrom(effectiveFrom).isCurrent(true).assignedByAccount(actor).build());
        audits.save(AuditLog.builder().actorAccount(actor).action("EMPLOYEE_ASSIGNMENT_CREATED")
                .entityType("EMPLOYEE_ASSIGNMENT").entityId(saved.getId())
                .oldData(oldData).newData(assignmentAuditData(saved))
                .occurredAt(OffsetDateTime.now()).build());
        return toResponse(saved);
    }

    @Override
    public EmployeeAssignmentResponse getCurrentAssignment(Long accountId) {
        Employee employee = employees.findByAccountId(accountId)
                .orElseThrow(() -> notFound("EMPLOYEE_PROFILE_NOT_FOUND", "Tài khoản chưa được gắn với hồ sơ nhân viên."));
        return assignments.findCurrentAssignmentByEmployeeId(employee.getId()).map(this::toResponse)
                .orElseThrow(() -> notFound("CURRENT_ASSIGNMENT_NOT_FOUND", "Nhân viên chưa có phân công hiện hành."));
    }

    @Override
    public PagedResponse<EmployeeAssignmentResponse> getAssignmentHistory(Long actorAccountId, Long employeeId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.");
        }
        accessScope.requireCanReadEmployee(actorAccountId, employeeId);
        if (!employees.existsById(employeeId)) {
            throw notFound("EMPLOYEE_NOT_FOUND", "Không tìm thấy nhân viên.");
        }
        Page<EmployeeAssignment> result = assignments.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employeeId,
                PageRequest.of(page, size, Sort.unsorted()));
        return PagedResponse.<EmployeeAssignmentResponse>builder().content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).hasNext(result.hasNext()).build();
    }

    private EmployeeAssignmentResponse toResponse(EmployeeAssignment assignment) {
        Employee employee = assignment.getEmployee();
        Employee manager = assignment.getManagerEmployee();
        return EmployeeAssignmentResponse.builder().id(assignment.getId())
                .employee(employeeSummary(employee)).department(departmentDto(assignment.getDepartment()))
                .position(positionDto(assignment.getPosition())).manager(employeeSummary(manager))
                .effectiveFrom(assignment.getEffectiveFrom()).effectiveTo(assignment.getEffectiveTo())
                .isCurrent(assignment.getIsCurrent()).assignedBy(assignedBy(assignment.getAssignedByAccount()))
                .createdAt(assignment.getCreatedAt()).build();
    }
    private EmployeeAssignmentResponse.EmployeeSummary employeeSummary(Employee employee) {
        return employee == null ? null : EmployeeAssignmentResponse.EmployeeSummary.builder().id(employee.getId())
                .employeeCode(employee.getEmployeeCode()).fullName(employee.getFullName()).build();
    }
    private DepartmentDto departmentDto(Department department) {
        return department == null ? null : DepartmentDto.builder().id(department.getId()).code(department.getCode()).name(department.getName()).build();
    }
    private PositionDto positionDto(Position position) {
        return position == null ? null : PositionDto.builder().id(position.getId()).code(position.getCode()).name(position.getName()).build();
    }
    private EmployeeAssignmentResponse.AssignedBy assignedBy(Account account) {
        return account == null ? null : EmployeeAssignmentResponse.AssignedBy.builder().accountId(account.getId()).username(account.getUsername()).build();
    }
    /** Chụp các PK và khoảng hiệu lực trước/sau để audit giải thích đúng người, danh mục và quản lý đã đổi; không ghi DB. */
    private String assignmentAuditData(EmployeeAssignment item) {
        return String.format(java.util.Locale.ROOT,
                "{\"employeeId\":%d,\"departmentId\":%s,\"positionId\":%s,\"managerEmployeeId\":%s,\"effectiveFrom\":\"%s\",\"effectiveTo\":%s,\"isCurrent\":%s}",
                item.getEmployee().getId(), item.getDepartment() == null ? "null" : item.getDepartment().getId().toString(),
                item.getPosition() == null ? "null" : item.getPosition().getId().toString(),
                item.getManagerEmployee() == null ? "null" : item.getManagerEmployee().getId().toString(),
                item.getEffectiveFrom(), item.getEffectiveTo() == null ? "null" : "\"" + item.getEffectiveTo() + "\"", item.getIsCurrent());
    }

    private void requireWorking(Employee employee, String message) {
        if (employee.getEmploymentStatus() != EmploymentStatus.WORKING) throw conflict("EMPLOYEE_NOT_WORKING", message);
    }
    private AuthException badRequest(String code, String message) { return new AuthException(code, message, 400); }
    private AuthException conflict(String code, String message) { return new AuthException(code, message, 409); }
    private ResourceNotFoundException notFound(String code, String message) { return new ResourceNotFoundException(code, message); }
}
