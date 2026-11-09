package com.hrm.backend.service;

import com.hrm.backend.dto.request.CreateSalaryProfileRequest;
import com.hrm.backend.dto.request.UpdateSalaryProfileRequest;
import com.hrm.backend.dto.response.PagedResponse;
import com.hrm.backend.dto.response.SalaryProfileEmployeeListItemResponse;
import com.hrm.backend.dto.response.SalaryProfileHistoryItemResponse;
import com.hrm.backend.dto.response.SalaryProfileHistoryResponse;
import com.hrm.backend.entity.enums.SalaryProfileState;

public interface PayrollSalaryProfileService {
    PagedResponse<SalaryProfileEmployeeListItemResponse> searchEmployees(
            String q, Long departmentId, SalaryProfileState profileState, int page, int size);

    SalaryProfileHistoryResponse getEmployeeSalaryProfileHistory(Long employeeId);

    SalaryProfileHistoryItemResponse createSalaryProfile(Long actorAccountId, CreateSalaryProfileRequest request);

    SalaryProfileHistoryItemResponse updateSalaryProfile(Long profileId, UpdateSalaryProfileRequest request);
}
