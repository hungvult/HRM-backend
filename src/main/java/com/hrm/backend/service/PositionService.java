package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionRequest;
import com.hrm.backend.dto.request.UpdatePositionStatusRequest;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.PositionResponse;
import com.hrm.backend.entity.enums.PositionStatus;

public interface PositionService {
    PositionResponse createPosition(Long actorAccountId, CreatePositionRequest request);
    PagedResponse<PositionResponse> searchPositions(String q, PositionStatus status, Integer rankLevel, int page, int size,
                                                     String sortBy, String sortDirection);
    PositionResponse updatePosition(Long actorAccountId, Long positionId, UpdatePositionRequest request);
    PositionResponse updatePositionStatus(Long actorAccountId, Long positionId, UpdatePositionStatusRequest request);

    PositionResponse getPosition(Long positionId);
}
