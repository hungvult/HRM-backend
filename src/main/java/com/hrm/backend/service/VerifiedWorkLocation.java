package com.hrm.backend.service;

import java.math.BigDecimal;

public record VerifiedWorkLocation(BigDecimal latitude, BigDecimal longitude, int distanceMeters) {
}
