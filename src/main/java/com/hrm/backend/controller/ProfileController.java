package com.hrm.backend.controller;

import com.hrm.backend.dto.request.UpdateMyProfileRequest;
import com.hrm.backend.dto.response.CurrentUserResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Personal-profile endpoints. The profile is always resolved from the
 * authenticated account, never from an identifier supplied by the client.
 */
@RestController
@RequestMapping("/v1/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật số điện thoại và địa chỉ cá nhân")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentUserResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateMyProfileRequest request) {
        return ResponseEntity.ok(
                userService.updateCurrentUserProfile(userDetails.getAccount().getId(), request));
    }
    @GetMapping("/profile")
    @Operation(summary = "Xem thông tin cá nhân")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentUserResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CurrentUserResponse profile = userService.getCurrentUserProfile(userDetails.getAccount().getId());
        return ResponseEntity.ok(profile);
    }

}
