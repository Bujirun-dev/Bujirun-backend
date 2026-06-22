package com.bujirun.bujirun.global.jwt.dto;

import lombok.Builder;
import lombok.Getter;

// 카카오 로그인 성공 후 프론트에 전달할 토큰 응답
@Getter
@Builder
public class TokenResponse {

    private String accessToken;  // 30분짜리, API 호출할 때 사용
    private String tokenType;    // "Bearer" 고정값
}