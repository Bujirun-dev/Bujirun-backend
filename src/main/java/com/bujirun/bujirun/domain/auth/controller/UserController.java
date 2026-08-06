package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.request.UpdateProfileRequest;
import com.bujirun.bujirun.domain.auth.dto.response.NicknameAvailabilityResponse;
import com.bujirun.bujirun.domain.auth.dto.response.UserProfileResponse;
import com.bujirun.bujirun.domain.auth.service.UserService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import com.bujirun.bujirun.global.jwt.RefreshTokenRepository;

import java.util.UUID;

@Tag(name = "사용자", description = "내 프로필 조회 및 수정 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;

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

    @Operation(summary = "닉네임 중복 확인", description = "닉네임을 실제로 변경하기 전에 사용 가능한 닉네임인지 미리 확인합니다.")
    @GetMapping("/me/nickname/availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNicknameAvailability(
            @AuthenticationPrincipal UUID userId,
            @RequestParam @Size(min = 1, max = 50, message = "닉네임은 1자 이상 50자 이하로 입력해주세요.") String nickname) {
        return ApiResponse.ok(userService.checkNicknameAvailability(userId, nickname));
    }

    // 회원 탈퇴
    @Operation(summary = "회원탈퇴", description = "개인정보를 익명화하고 인증 정보를 만료시킵니다. 여행 데이터는 유지됩니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMyAccount(
            @AuthenticationPrincipal UUID userId,
            HttpServletResponse response) {

        // 1. Redis에 저장된 Refresh Token 삭제
        refreshTokenRepository.delete(userId);

        // 2. 클라이언트 쿠키 만료 처리
        ResponseCookie expiredCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        // 3. 유저 및 연관 데이터 삭제
        userService.deleteAccount(userId);

        return ApiResponse.ok(null);
    }
}
