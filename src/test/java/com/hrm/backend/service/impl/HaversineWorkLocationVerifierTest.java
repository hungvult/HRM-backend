package com.hrm.backend.service.impl;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.entity.WorkLocation;
import com.hrm.backend.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HaversineWorkLocationVerifierTest {

    private static final Instant NOW = Instant.parse("2026-10-17T02:15:00Z");
    private final HaversineWorkLocationVerifier verifier = new HaversineWorkLocationVerifier(properties(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final WorkLocation location = WorkLocation.builder()
            .latitude(new BigDecimal("10.776889"))
            .longitude(new BigDecimal("106.700806"))
            .allowedRadiusMeters(100)
            .isActive(true)
            .build();

    @Test
    void verify_whenCoordinateIsInsideRadius_returnsServerCalculatedDistance() {
        var result = verifier.verify(
                request(new BigDecimal("10.776900"), new BigDecimal("106.700806")),
                location);

        assertEquals(2, result.distanceMeters());
    }

    @Test
    void verify_whenCoordinateIsOutsideRadius_rejectsAttendance() {
        AuthException exception = assertThrows(AuthException.class, () -> verifier.verify(
                request(new BigDecimal("10.780000"), new BigDecimal("106.700806")),
                location));

        assertEquals(403, exception.getStatus());
        assertEquals("ATTENDANCE_OUTSIDE_WORK_LOCATION", exception.getCode());
    }

    @Test
    void verify_whenCoordinateIsInvalid_returnsVietnameseValidationError() {
        AuthException exception = assertThrows(AuthException.class, () -> verifier.verify(
                request(new BigDecimal("91"), BigDecimal.ZERO), location));

        assertEquals(400, exception.getStatus());
        assertEquals("ATTENDANCE_INVALID_GPS", exception.getCode());
    }

    @Test
    void verify_whenAccuracyIsWorseThanConfiguredLimit_rejectsAttendance() {
        AuthException exception = assertThrows(AuthException.class, () -> verifier.verify(
                new AttendanceLocationRequest(new BigDecimal("10.776900"), new BigDecimal("106.700806"),
                        new BigDecimal("51"), NOW), location));

        assertEquals(400, exception.getStatus());
        assertEquals("ATTENDANCE_INVALID_GPS", exception.getCode());
    }

    @Test
    void verify_whenLocationIsOlderThanConfiguredLimit_rejectsAttendance() {
        AuthException exception = assertThrows(AuthException.class, () -> verifier.verify(
                new AttendanceLocationRequest(new BigDecimal("10.776900"), new BigDecimal("106.700806"),
                        new BigDecimal("10"), NOW.minusSeconds(61)), location));

        assertEquals(400, exception.getStatus());
        assertEquals("ATTENDANCE_INVALID_GPS", exception.getCode());
    }

    private static AttendanceLocationRequest request(BigDecimal latitude, BigDecimal longitude) {
        return new AttendanceLocationRequest(latitude, longitude, new BigDecimal("10"), NOW);
    }

    private static com.hrm.backend.config.AttendanceProperties properties() {
        com.hrm.backend.config.AttendanceProperties properties = new com.hrm.backend.config.AttendanceProperties();
        properties.setMaxGpsAccuracyMeters(50);
        properties.setMaxLocationAgeSeconds(60);
        properties.setMaxFutureLocationSeconds(15);
        return properties;
    }
}
