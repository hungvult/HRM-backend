package com.hrm.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MonthlyAttendanceResponse {
    private final String month;
    private final List<AttendanceListItemResponse> items;
}
