package com.hrm.backend.service.impl;

import com.hrm.backend.config.AttendanceProperties;
import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.entity.WorkLocation;
import com.hrm.backend.exception.AuthException;
import com.hrm.backend.service.VerifiedWorkLocation;
import com.hrm.backend.service.WorkLocationVerifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HaversineWorkLocationVerifier implements WorkLocationVerifier {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private final AttendanceProperties properties;
    private final Clock attendanceClock;

    @Override
    public VerifiedWorkLocation verify(AttendanceLocationRequest location, WorkLocation workLocation) {
        requireValidCoordinates(location);
        requireAcceptableGpsQuality(location);
        if (workLocation == null || !Boolean.TRUE.equals(workLocation.getIsActive())
                || workLocation.getLatitude() == null || workLocation.getLongitude() == null
                || workLocation.getAllowedRadiusMeters() == null || workLocation.getAllowedRadiusMeters() <= 0) {
            throw new IllegalStateException("Địa điểm làm việc chưa được cấu hình hợp lệ.");
        }

        int distanceMeters = (int) Math.ceil(calculateDistanceMeters(
                location.getLatitude(), location.getLongitude(),
                workLocation.getLatitude(), workLocation.getLongitude()));
        if (distanceMeters > workLocation.getAllowedRadiusMeters()) {
            throw new AuthException(
                    "ATTENDANCE_OUTSIDE_WORK_LOCATION",
                    "Không thể chấm công ngoài khu vực làm việc cho phép.",
                    403);
        }
        return new VerifiedWorkLocation(location.getLatitude(), location.getLongitude(), distanceMeters);
    }

    private void requireValidCoordinates(AttendanceLocationRequest location) {
        if (location == null || location.getLatitude() == null || location.getLongitude() == null
                || location.getLatitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || location.getLatitude().compareTo(BigDecimal.valueOf(90)) > 0
                || location.getLongitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || location.getLongitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new AuthException("ATTENDANCE_INVALID_GPS", "Tọa độ GPS không hợp lệ.", 400);
        }
    }

    private void requireAcceptableGpsQuality(AttendanceLocationRequest location) {
        if (location.getAccuracyMeters() == null
                || location.getAccuracyMeters().compareTo(BigDecimal.ZERO) <= 0
                || location.getAccuracyMeters().compareTo(BigDecimal.valueOf(properties.getMaxGpsAccuracyMeters())) > 0) {
            throw invalidGps("Độ chính xác GPS không đạt yêu cầu.");
        }
        Instant capturedAt = location.getLocationCapturedAt();
        if (capturedAt == null) {
            throw invalidGps("Thời điểm lấy vị trí GPS không hợp lệ.");
        }
        Instant now = attendanceClock.instant();
        if (capturedAt.isBefore(now.minusSeconds(properties.getMaxLocationAgeSeconds()))
                || capturedAt.isAfter(now.plusSeconds(properties.getMaxFutureLocationSeconds()))) {
            throw invalidGps("Vị trí GPS đã quá cũ hoặc thời điểm không hợp lệ.");
        }
    }

    private AuthException invalidGps(String message) {
        return new AuthException("ATTENDANCE_INVALID_GPS", message, 400);
    }

    private double calculateDistanceMeters(BigDecimal latitude, BigDecimal longitude,
                                           BigDecimal locationLatitude, BigDecimal locationLongitude) {
        double latitudeDelta = Math.toRadians(locationLatitude.doubleValue() - latitude.doubleValue());
        double longitudeDelta = Math.toRadians(locationLongitude.doubleValue() - longitude.doubleValue());
        double originLatitude = Math.toRadians(latitude.doubleValue());
        double targetLatitude = Math.toRadians(locationLatitude.doubleValue());
        double a = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(originLatitude) * Math.cos(targetLatitude)
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
