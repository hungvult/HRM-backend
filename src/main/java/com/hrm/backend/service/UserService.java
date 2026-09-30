package com.hrm.backend.service;

import com.hrm.backend.dto.request.UpdateMyProfileRequest;
import com.hrm.backend.dto.response.CurrentUserResponse;
public interface UserService {
    CurrentUserResponse getCurrentUserProfile(Long accountId);
    CurrentUserResponse updateCurrentUserProfile(Long accountId, UpdateMyProfileRequest request);
}
