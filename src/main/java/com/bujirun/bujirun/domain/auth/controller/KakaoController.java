package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.service.KakaoService;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoController {

    private final KakaoService kakaoService;

    // 카카오 로그인 콜백 엔드포인트
    // - 카카오 콘솔에 등록한 리다이렉트 URI와 경로가 정확히 일치해야 함
    @GetMapping("/api/auth/kakao/callback")
    public ApiResponse<TokenResponse> kakaoCallback(
            @RequestParam("code") String code,
            HttpServletResponse response) {

        // 1단계: 인가 코드 -> 카카오 액세스 토큰
        KakaoTokenResponse tokenResponse = kakaoService.getToken(code);

        // 2단계: 액세스 토큰 -> 사용자 정보
        KakaoUserInfoResponse userInfo = kakaoService.getUserInfo(tokenResponse.getAccessToken());

        // 3단계: DB 회원가입/로그인 처리
        User user = kakaoService.findOrCreateUser(userInfo);

        // 4단계: JWT 발급
        TokenResponse token = kakaoService.createToken(user);

        // Refresh Token을 httpOnly 쿠키에 저장 (TODO: Redis 연동 후 추가 예정)
        Cookie refreshCookie = new Cookie("refresh_token",
                kakaoService.createRefreshToken(user));
        refreshCookie.setHttpOnly(true);  // JS에서 접근 불가 (보안)
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(60 * 60 * 24 * 14); // 2주
        response.addCookie(refreshCookie);

        // Access Token은 응답 바디로 전달
        return ApiResponse.ok(token);
    }
}