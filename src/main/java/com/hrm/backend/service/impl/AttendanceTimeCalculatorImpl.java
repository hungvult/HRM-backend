package com.hrm.backend.service.impl;

import com.hrm.backend.entity.enums.AttendanceStatus;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.service.AttendanceTimeCalculator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Service
public class AttendanceTimeCalculatorImpl implements AttendanceTimeCalculator {
    @Override
    public Integer calculateWorkingMinutes(LocalDate workDate, OffsetDateTime checkInAt, OffsetDateTime checkOutAt,
                                           AttendanceStatus status) {
        if ((checkInAt != null && !checkInAt.toLocalDate().equals(workDate))
                || (checkOutAt != null && !checkOutAt.toLocalDate().equals(workDate))) {
            throw validation("Giờ vào và giờ ra phải thuộc đúng ngày công.");
        }
        if ((checkInAt == null) != (checkOutAt == null)) {
            if (status != AttendanceStatus.INCOMPLETE) {
                throw validation("Bản ghi thiếu giờ vào hoặc giờ ra phải có trạng thái INCOMPLETE.");
            }
            return null;
        }
        if (checkInAt == null) return null;
        if (checkOutAt.isBefore(checkInAt)) {
            throw validation("Giờ ra không được trước giờ vào.");
        }
        long minutes = Duration.between(checkInAt, checkOutAt).toMinutes();
        if (minutes > Integer.MAX_VALUE) {
            throw validation("Khoảng thời gian làm việc không hợp lệ.");
        }
        return (int) minutes;
    }

    private AuthException validation(String message) {
        return new AuthException("VALIDATION_ERROR", message, 400);
    }
}
