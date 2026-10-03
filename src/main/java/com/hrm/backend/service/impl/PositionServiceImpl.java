package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.CreatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionStatusRequest;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PositionResponse;
import com.hrm.backend.entity.Account;
import com.hrm.backend.entity.AuditLog;
import com.hrm.backend.entity.Position;
import com.hrm.backend.entity.enums.PositionStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.exception.ResourceNotFoundException;
import com.hrm.backend.repository.AccountRepository;
import com.hrm.backend.repository.AuditLogRepository;
import com.hrm.backend.repository.EmployeeAssignmentRepository;
import com.hrm.backend.repository.PositionRepository;
import com.hrm.backend.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionServiceImpl implements PositionService {
    private final PositionRepository positions;
    private final EmployeeAssignmentRepository assignments;
    private final AccountRepository accounts;
    private final AuditLogRepository audits;

    @Override
    @Transactional
    public PositionResponse createPosition(Long actorAccountId, CreatePositionRequest request) {
        String name = request.getName().trim();
        if (positions.existsByNameIgnoreCaseAndStatus(name, PositionStatus.ACTIVE)) {
            throw conflict("POSITION_NAME_ALREADY_EXISTS", "Tên chức vụ đang được sử dụng.");
        }
        Account actor = actor(actorAccountId);
        Position position = positions.saveAndFlush(Position.builder()
                .code(temporaryCode())
                .name(name)
                .description(normalizeDescription(request.getDescription()))
                .rankLevel(request.getRankLevel())
                .status(PositionStatus.ACTIVE)
                .build());
        position.setCode(generateCode(position.getId()));
        position = positions.save(position);
        audits.save(AuditLog.builder().actorAccount(actor).action("POSITION_CREATED").entityType("POSITION")
                .entityId(position.getId()).newData(positionAuditData(position)).build());
        return toResponse(position);
    }

    @Override
    public PagedResponse<PositionResponse> searchPositions(String q, PositionStatus status, Integer rankLevel, int page, int size,
                                                            String sortBy, String sortDirection) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AuthException("VALIDATION_ERROR", "page phải từ 0 và size phải trong khoảng 1 đến 100.", 400);
        }
        if (rankLevel != null && (rankLevel < 1 || rankLevel > 10)) {
            throw new AuthException("VALIDATION_ERROR", "rankLevel phải trong khoảng 1 đến 10.", 400);
        }
        if (!Set.of("code", "name", "rankLevel", "createdAt", "updatedAt").contains(sortBy)) {
            throw new AuthException("VALIDATION_ERROR", "sortBy không hợp lệ.", 400);
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new AuthException("VALIDATION_ERROR", "sortDirection chỉ nhận asc hoặc desc.", 400);
        }
        Page<Position> positionsPage = positions.searchPositions(q == null ? "" : q.trim(), status, rankLevel,
                PageRequest.of(page, size, Sort.by(direction, sortBy)));
        return PagedResponse.<PositionResponse>builder()
                .content(positionsPage.getContent().stream().map(this::toResponse).toList())
                .page(positionsPage.getNumber()).size(positionsPage.getSize())
                .totalElements(positionsPage.getTotalElements()).totalPages(positionsPage.getTotalPages())
                .hasNext(positionsPage.hasNext()).build();
    }

    @Override
    @Transactional
    public PositionResponse updatePosition(Long actorAccountId, Long positionId, UpdatePositionRequest request) {
        Position position = findPosition(positionId);
        String name = request.getName().trim();
        if (position.getStatus() == PositionStatus.ACTIVE
                && positions.existsByNameIgnoreCaseAndStatusAndIdNot(name, PositionStatus.ACTIVE, positionId)) {
            throw conflict("POSITION_NAME_ALREADY_EXISTS", "Tên chức vụ đang được sử dụng.");
        }
        String oldData = positionAuditData(position);
        position.setName(name);
        position.setDescription(normalizeDescription(request.getDescription()));
        position.setRankLevel(request.getRankLevel());
        position = positions.saveAndFlush(position);
        audits.save(AuditLog.builder().actorAccount(actor(actorAccountId)).action("POSITION_UPDATED")
                .entityType("POSITION").entityId(position.getId()).oldData(oldData)
                .newData(positionAuditData(position)).build());
        return toResponse(position);
    }

    @Override
    @Transactional
    public PositionResponse updatePositionStatus(Long actorAccountId, Long positionId, UpdatePositionStatusRequest request) {
        Position position = findPosition(positionId);
        PositionStatus newStatus = request.getStatus();
        if (position.getStatus() == newStatus) {
            throw new AuthException("POSITION_STATUS_UNCHANGED", "Trạng thái mới phải khác trạng thái hiện tại.", 400);
        }
        if (newStatus == PositionStatus.INACTIVE && assignments.existsByPositionIdAndIsCurrentTrue(positionId)) {
            throw conflict("POSITION_HAS_ACTIVE_ASSIGNMENTS",
                    "Không thể vô hiệu hóa chức vụ đang có nhân viên được phân công.");
        }
        String oldData = statusAuditData(position.getStatus(), null);
        position.setStatus(newStatus);
        position = positions.saveAndFlush(position);
        audits.save(AuditLog.builder().actorAccount(actor(actorAccountId)).action("POSITION_STATUS_UPDATED")
                .entityType("POSITION").entityId(position.getId()).oldData(oldData)
                .newData(statusAuditData(newStatus, normalizeReason(request.getReason()))).build());
        return toResponse(position);
    }

    @Override
    public PositionResponse getPosition(Long positionId) {
        if (positionId == null || positionId < 1) {
            throw new AuthException("VALIDATION_ERROR", "ID chức vụ không hợp lệ.", 400);
        }
        return toResponse(findPosition(positionId));
    }

    private Position findPosition(Long positionId) {
        return positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("POSITION_NOT_FOUND", "Không tìm thấy chức vụ."));
    }

    private Account actor(Long actorAccountId) {
        return accounts.findById(actorAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản thực hiện."));
    }

    private PositionResponse toResponse(Position position) {
        return PositionResponse.builder().id(position.getId()).code(position.getCode()).name(position.getName())
                .description(position.getDescription()).rankLevel(position.getRankLevel()).status(position.getStatus().name())
                .createdAt(position.getCreatedAt()).updatedAt(position.getUpdatedAt()).build();
    }

    private String temporaryCode() {
        return "TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private String generateCode(Long positionId) {
        return String.format(Locale.ROOT, "CV%06d", positionId);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private String positionAuditData(Position position) {
        return String.format(Locale.ROOT,
                "{\"code\":\"%s\",\"name\":\"%s\",\"description\":%s,\"rankLevel\":%d,\"status\":\"%s\"}",
                escapeJson(position.getCode()), escapeJson(position.getName()), jsonStringOrNull(position.getDescription()),
                position.getRankLevel(), position.getStatus().name());
    }

    private String statusAuditData(PositionStatus status, String reason) {
        return String.format(Locale.ROOT, "{\"status\":\"%s\",\"reason\":%s}",
                status.name(), jsonStringOrNull(reason));
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
