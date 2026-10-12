package com.hrm.backend.service;

import com.hrm.backend.dto.request.AttendanceLocationRequest;
import com.hrm.backend.entity.WorkLocation;

public interface WorkLocationVerifier {
    VerifiedWorkLocation verify(AttendanceLocationRequest location, WorkLocation workLocation);
}
