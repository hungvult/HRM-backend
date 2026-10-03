package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentRequest;
import com.hrm.backend.dto.request.UpdateDepartmentStatusRequest;
import com.hrm.backend.dto.response.DepartmentResponse;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.entity.enums.DepartmentStatus;

public interface DepartmentService {
    DepartmentResponse createDepartment(Long actorAccountId, CreateDepartmentRequest request);

    DepartmentResponse updateDepartment(Long actorAccountId, Long departmentId, UpdateDepartmentRequest request);

    DepartmentResponse updateDepartmentStatus(Long actorAccountId, Long departmentId, UpdateDepartmentStatusRequest request);

    PagedResponse<DepartmentResponse> searchDepartments(String q, DepartmentStatus status, int page, int size,
                                                         String sortBy, String sortDirection);
}
