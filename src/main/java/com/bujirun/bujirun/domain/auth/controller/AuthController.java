package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.global.jwt.JwtProvider;
import com.bujirun.bujirun.global.jwt.RefreshTokenRepository;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    // Access Token 재발급 API
    // 쿠키의 Refresh Token으로 새 Access Token 발급
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(HttpServletRequest request) {

        // 쿠키에서 Refresh Token 꺼내기
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new IllegalArgumentException("쿠키가 없습니다");
        }

        String refreshToken = Arrays.stream(cookies)
                .filter(c -> c.getName().equals("refresh_token"))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new IllegalArgumentException("Refresh Token이 없습니다"));

        // Refresh Token 유효성 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다");
        }

        // 토큰에서 유저 ID 추출
        UUID userId = jwtProvider.extractUserId(refreshToken);

        // Redis에 저장된 Refresh Token과 비교
        String savedToken = refreshTokenRepository.find(userId);
        if (!refreshToken.equals(savedToken)) {
            throw new IllegalArgumentException("Refresh Token이 일치하지 않습니다");
        }

        // 새 Access Token 발급
        TokenResponse token = TokenResponse.builder()
                .accessToken(jwtProvider.createAccessToken(userId))
                .tokenType("Bearer")
                .build();

        return ApiResponse.ok(token);
    }
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {

        // 쿠키에서 Refresh Token 꺼내기
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Arrays.stream(cookies)
                    .filter(c -> c.getName().equals("refresh_token"))
                    .findFirst()
                    .ifPresent(c -> {
                        // Redis에서 Refresh Token 삭제
                        UUID userId = jwtProvider.extractUserId(c.getValue());
                        refreshTokenRepository.delete(userId);
                    });
        }

        // HttpOnly Cookie 만료 처리
        Cookie cookie = new Cookie("refresh_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ApiResponse.ok(null);
    }
}