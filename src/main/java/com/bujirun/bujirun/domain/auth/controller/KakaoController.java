package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.dto.UserAuthResult;
import com.bujirun.bujirun.domain.auth.dto.response.KakaoLoginResponse;
import com.bujirun.bujirun.domain.auth.service.KakaoService;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;  // GET → POST로 변경
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoController {

    private final KakaoService kakaoService;

    // 변경: GET /api/auth/kakao/callback → POST /api/auth/kakao/token
    @PostMapping("/api/auth/kakao/token")
    public ApiResponse<KakaoLoginResponse> kakaoLogin(
            @RequestParam("code") String code,
            HttpServletResponse response) {

        KakaoTokenResponse tokenResponse = kakaoService.getToken(code);
        KakaoUserInfoResponse userInfo = kakaoService.getUserInfo(tokenResponse.getAccessToken());
        UserAuthResult authResult = kakaoService.findOrCreateUser(userInfo);
        TokenResponse token = kakaoService.createToken(authResult.user());

        Cookie refreshCookie = new Cookie("refresh_token",
                kakaoService.createRefreshToken(authResult.user()));
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(60 * 60 * 24 * 14);
        response.addCookie(refreshCookie);

        return ApiResponse.ok(KakaoLoginResponse.of(token, authResult.isNewUser()));
    }
}