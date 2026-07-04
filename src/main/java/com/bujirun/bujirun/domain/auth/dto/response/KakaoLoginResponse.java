package com.bujirun.bujirun.domain.auth.dto.response;

import com.bujirun.bujirun.global.jwt.dto.TokenResponse;

public record KakaoLoginResponse(
        String accessToken,
        String tokenType,
        boolean isNewUser
) {
    public static KakaoLoginResponse of(TokenResponse token, boolean isNewUser) {
        return new KakaoLoginResponse(token.getAccessToken(), token.getTokenType(), isNewUser);
    }
}
