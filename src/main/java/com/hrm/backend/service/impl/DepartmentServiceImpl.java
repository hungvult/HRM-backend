package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentStatusRequest;
import com.hrm.backend.dto.response.DepartmentResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AuditLog;
import com.hrm.backend.entity.Department;
import com.hrm.backend.entity.enums.DepartmentStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AuditLogRepository;
import com.hrm.backend.repository.DepartmentRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentServiceImpl implements DepartmentService {
    private final DepartmentRepository departments;
    private final EmployeeAssignmentRepository assignments;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;

    @Override
    @Transactional
    public DepartmentResponse createDepartment(Long actorAccountId, CreateDepartmentRequest request) {
        String code = request.getCode().trim().toUpperCase(Locale.ROOT);
        String name = request.getName().trim();

        if (departments.existsByCodeIgnoreCase(code)) {
            throw conflict("DEPARTMENT_CODE_ALREADY_EXISTS", "Mã phòng ban đã tồn tại.");
        }
        if (departments.existsByNameIgnoreCaseAndStatus(name, DepartmentStatus.ACTIVE)) {
            throw conflict("DEPARTMENT_NAME_ALREADY_EXISTS", "Tên phòng ban đang được sử dụng.");
        }

        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));

        Department department = departments.save(Department.builder()
                .code(code)
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .status(DepartmentStatus.ACTIVE)
                .build());

        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("DEPARTMENT_CREATED")
                .entityType("DEPARTMENT")
                .entityId(department.getId())
                .newData(String.format(Locale.ROOT, "{\"code\":\"%s\",\"name\":\"%s\",\"status\":\"ACTIVE\"}",
                        escapeJson(department.getCode()), escapeJson(department.getName())))
                .build());

        return toResponse(department);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartment(Long actorAccountId, Long departmentId, UpdateDepartmentRequest request) {
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "Không tìm thấy phòng ban."));
        String name = request.getName().trim();

        if (department.getStatus() == DepartmentStatus.ACTIVE
                && departments.existsByNameIgnoreCaseAndStatusAndIdNot(name, DepartmentStatus.ACTIVE, departmentId)) {
            throw conflict("DEPARTMENT_NAME_ALREADY_EXISTS", "Tên phòng ban đang được sử dụng.");
        }

        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        String oldData = departmentAuditData(department);
        department.setName(name);
        department.setDescription(normalizeDescription(request.getDescription()));
        department = departments.save(department);

        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("DEPARTMENT_UPDATED")
                .entityType("DEPARTMENT")
                .entityId(department.getId())
                .oldData(oldData)
                .newData(departmentAuditData(department))
                .build());

        return toResponse(department);
    }

    @Override
    @Transactional
    public DepartmentResponse updateDepartmentStatus(Long actorAccountId, Long departmentId,
                                                     UpdateDepartmentStatusRequest request) {
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "Không tìm thấy phòng ban."));
        DepartmentStatus newStatus = request.getStatus();
        if (department.getStatus() == newStatus) {
            throw new AuthException("DEPARTMENT_STATUS_UNCHANGED", "Trạng thái mới phải khác trạng thái hiện tại.", 400);
        }
        if (newStatus == DepartmentStatus.INACTIVE && assignments.existsByDepartmentIdAndIsCurrentTrue(departmentId)) {
            throw conflict("DEPARTMENT_HAS_ACTIVE_ASSIGNMENTS",
                    "Không thể vô hiệu hóa phòng ban đang có nhân viên được phân công.");
        }

        Account actor = accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
        String oldData = statusAuditData(department.getStatus(), null);
        department.setStatus(newStatus);
        department = departments.saveAndFlush(department);

        audits.save(AuditLog.builder()
                .actorAccount(actor)
                .action("DEPARTMENT_STATUS_UPDATED")
                .entityType("DEPARTMENT")
                .entityId(department.getId())
                .oldData(oldData)
                .newData(statusAuditData(newStatus, normalizeReason(request.getReason())))
                .build());

        return toResponse(department);
    }

    @Override
    public DepartmentResponse getDepartment(Long departmentId) {
        if (departmentId == null || departmentId < 1) {
            throw new AuthException("VALIDATION_ERROR", "ID phòng ban không hợp lệ.", 400);
        }
        Department department = departments.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("DEPARTMENT_NOT_FOUND", "Không tìm thấy phòng ban."));
        return toResponse(department);
    }

    @Override
    public PagedResponse<DepartmentResponse> searchDepartments(String q, DepartmentStatus status, int page, int size,
                                                                String sortBy, String sortDirection) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }

        Set<String> allowedSortFields = Set.of("code", "name", "createdAt", "updatedAt");
        if (!allowedSortFields.contains(sortBy)) {
            throw new AuthException("VALIDATION_ERROR", "sortBy không hợp lệ.", 400);
        }

        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new AuthException("VALIDATION_ERROR", "sortDirection chỉ nhận asc hoặc desc.", 400);
        }

        Page<Department> departmentsPage = departments.searchDepartments(
                q == null ? "" : q.trim(), status, PageRequest.of(page, size, Sort.by(direction, sortBy)));
        return PagedResponse.<DepartmentResponse>builder()
                .content(departmentsPage.getContent().stream().map(this::toResponse).toList())
                .page(departmentsPage.getNumber())
                .size(departmentsPage.getSize())
                .totalElements(departmentsPage.getTotalElements())
                .totalPages(departmentsPage.getTotalPages())
                .hasNext(departmentsPage.hasNext())
                .build();
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .description(department.getDescription())
                .status(department.getStatus().name())
                .createdAt(department.getCreatedAt())
                .updatedAt(department.getUpdatedAt())
                .build();
    }

    private String departmentAuditData(Department department) {
        return String.format(Locale.ROOT,
                "{\"code\":\"%s\",\"name\":\"%s\",\"description\":%s,\"status\":\"%s\"}",
                escapeJson(department.getCode()), escapeJson(department.getName()),
                jsonStringOrNull(department.getDescription()), department.getStatus().name());
    }

    private String statusAuditData(DepartmentStatus status, String reason) {
        return String.format(Locale.ROOT, "{\"status\":\"%s\",\"reason\":%s}",
                status.name(), jsonStringOrNull(reason));
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String jsonStringOrNull(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private AuthException conflict(String code, String message) {
        return new AuthException(code, message, 409);
    }
}
