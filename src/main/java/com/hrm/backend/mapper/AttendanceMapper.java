package com.hrm.backend.mapper;

import com.hrm.backend.dto.response.AttendanceDetailResponse;
import com.hrm.backend.dto.response.AttendanceManagementListItemResponse;
import com.hrm.backend.dto.response.DepartmentDto;
import com.hrm.backend.entity.AttendanceRecord;
import com.hrm.backend.entity.EmployeeAssignment;
import org.springframework.stereotype.Component;

@Component
public class AttendanceMapper {

    public AttendanceManagementListItemResponse toListItem(AttendanceRecord record, EmployeeAssignment assignment) {
        return AttendanceManagementListItemResponse.builder()
                .id(record.getId())
                .employeeId(record.getEmployee().getId())
                .employeeCode(record.getEmployee().getEmployeeCode())
                .employeeName(record.getEmployee().getFullName())
                .department(toDepartment(assignment))
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .workingMinutes(record.getWorkingMinutes())
                .attendanceStatus(record.getAttendanceStatus())
                .manuallyAdjusted(record.getIsManuallyAdjusted())
                .build();
    }

    public AttendanceDetailResponse toDetail(AttendanceRecord record, EmployeeAssignment assignment) {
        return AttendanceDetailResponse.builder()
                .id(record.getId())
                .employeeId(record.getEmployee().getId())
                .employeeCode(record.getEmployee().getEmployeeCode())
                .employeeName(record.getEmployee().getFullName())
                .department(toDepartment(assignment))
                .workDate(record.getWorkDate())
                .checkInAt(record.getCheckInAt())
                .checkOutAt(record.getCheckOutAt())
                .checkInLatitude(record.getCheckInLatitude())
                .checkInLongitude(record.getCheckInLongitude())
                .checkOutLatitude(record.getCheckOutLatitude())
                .checkOutLongitude(record.getCheckOutLongitude())
                .checkInWifiSsid(record.getCheckInWifiSsid())
                .checkOutWifiSsid(record.getCheckOutWifiSsid())
                .workingMinutes(record.getWorkingMinutes())
                .attendanceStatus(record.getAttendanceStatus())
                .manuallyAdjusted(record.getIsManuallyAdjusted())
                .adjustmentReason(record.getAdjustmentReason())
                .adjustedByAccountId(record.getAdjustedByAccount() == null ? null : record.getAdjustedByAccount().getId())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private DepartmentDto toDepartment(EmployeeAssignment assignment) {
        if (assignment == null || assignment.getDepartment() == null) {
            return null;
        }
        return DepartmentDto.builder()
                .id(assignment.getDepartment().getId())
                .code(assignment.getDepartment().getCode())
                .name(assignment.getDepartment().getName())
                .build();
    }
}
