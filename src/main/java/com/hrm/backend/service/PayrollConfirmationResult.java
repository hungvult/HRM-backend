package com.hrm.backend.service;

import com.hrm.backend.dto.response.PayrollRunResponse;

public record PayrollConfirmationResult(PayrollRunResponse run, boolean created) { }
