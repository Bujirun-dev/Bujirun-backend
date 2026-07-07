package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.request.UpdateProfileRequest;
import com.bujirun.bujirun.domain.auth.dto.response.UserProfileResponse;
import com.bujirun.bujirun.domain.auth.service.UserService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "사용자", description = "내 프로필 조회 및 수정 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회", description = "로그인한 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getMyProfile(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(userService.getProfile(userId));
    }

    @Operation(summary = "내 프로필 수정", description = "로그인한 사용자의 닉네임, 프로필 이미지 등 프로필 정보를 수정합니다.")
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UUID userId,
            @RequestBody @Valid UpdateProfileRequest req) {
        return ApiResponse.ok(userService.updateProfile(userId, req));
    }
}
