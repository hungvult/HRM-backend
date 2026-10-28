package com.hrm.backend.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LeaveBalanceListResponse {
    int year;
    List<LeaveBalanceResponse> items;
}
