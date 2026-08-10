package com.bujirun.bujirun.domain.auth.dto.response;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;

import java.util.UUID;

public record KakaoLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UUID userId,
        String nickname,
        String profileImageUrl
) {
    public static KakaoLoginResponse of(TokenResponse token, boolean isNewUser, User user) {
        return new KakaoLoginResponse(
                token.getAccessToken(),
                token.getTokenType(),
                token.getExpiresIn(),
                isNewUser,
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
