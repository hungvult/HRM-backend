package com.hrm.backend.dto.request;

import jakarta.validation.constraints.Size;

public record CancelLeaveRequest(@Size(max = 1000, message = "Lý do hủy không được quá 1.000 ký tự.") String reason) { }
