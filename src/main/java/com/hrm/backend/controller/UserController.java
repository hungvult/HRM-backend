package com.hrm.backend.controller;

import com.hrm.backend.dto.response.CurrentUserResponse;
import com.hrm.backend.security.CustomUserDetails;
import com.hrm.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin người dùng hiện tại")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        CurrentUserResponse userProfile = userService.getCurrentUserProfile(userDetails.getAccount().getId());
        return ResponseEntity.ok(userProfile);
    }
}
