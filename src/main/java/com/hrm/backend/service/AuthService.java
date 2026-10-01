package com.hrm.backend.service;

import com.hrm.backend.dto.request.LoginRequest;
import com.hrm.backend.dto.response.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    LoginResponse refreshToken(String refreshToken);
    void logout(Long accountId, Long sessionId);
}
