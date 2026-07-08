package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.dto.UserAuthResult;
import com.bujirun.bujirun.domain.auth.dto.response.KakaoLoginResponse;
import com.bujirun.bujirun.domain.auth.service.KakaoService;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;  // GET → POST로 변경
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카카오 로그인", description = "카카오 소셜 로그인 API")
@RestController
@RequiredArgsConstructor
public class KakaoController {

    private final KakaoService kakaoService;

    // 변경: GET /api/auth/kakao/callback → POST /api/auth/kakao/token
    @Operation(summary = "카카오 로그인", description = "카카오 인가 코드로 액세스 토큰과 사용자 정보를 받아 회원가입/로그인을 처리하고 서비스 자체 토큰을 발급합니다.")
    @PostMapping("/api/auth/kakao/token")
    public ApiResponse<KakaoLoginResponse> kakaoLogin(
            @RequestParam("code") String code,
            HttpServletResponse response) {

        KakaoTokenResponse tokenResponse = kakaoService.getToken(code);
        KakaoUserInfoResponse userInfo = kakaoService.getUserInfo(tokenResponse.getAccessToken());
        UserAuthResult authResult = kakaoService.findOrCreateUser(userInfo);
        TokenResponse token = kakaoService.createToken(authResult.user());

        ResponseCookie refreshCookie = ResponseCookie.from(
                        "refresh_token",
                        kakaoService.createRefreshToken(authResult.user()))
                .httpOnly(true)
                .secure(true)        // https 환경 필수 (SameSite=None은 Secure 동반 필수)
                .sameSite("None")    // 크로스도메인(localhost:3000 ↔ api.bujirun.store) 허용
                .path("/")
                .maxAge(60 * 60 * 24 * 14)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ApiResponse.ok(KakaoLoginResponse.of(token, authResult.isNewUser()));
    }
}