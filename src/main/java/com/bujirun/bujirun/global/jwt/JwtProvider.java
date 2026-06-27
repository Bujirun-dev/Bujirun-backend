package com.bujirun.bujirun.global.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

// JWT 토큰 생성 및 검증 담당
@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    // Access Token 생성
    public String createAccessToken(UUID userId) {
        return JWT.create()
                .withSubject(userId.toString())      // 토큰 주인 (유저 ID)
                .withIssuedAt(new Date())             // 발급 시간
                .withExpiresAt(new Date(System.currentTimeMillis()
                        + jwtProperties.getAccessTokenExpiration())) // 만료 시간
                .sign(Algorithm.HMAC256(jwtProperties.getSecret())); // 서명
    }

    // Refresh Token 생성
    public String createRefreshToken(UUID userId) {
        return JWT.create()
                .withSubject(userId.toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis()
                        + jwtProperties.getRefreshTokenExpiration()))
                .sign(Algorithm.HMAC256(jwtProperties.getSecret()));
    }

    // 토큰에서 유저 ID 추출
    public UUID extractUserId(String token) {
        String subject = JWT.require(Algorithm.HMAC256(jwtProperties.getSecret()))
                .build()
                .verify(token)
                .getSubject();
        return UUID.fromString(subject);
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(jwtProperties.getSecret()))
                    .build()
                    .verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    // Access Token + Refresh Token 한 번에 생성
    public TokenResponse createTokenResponse(UUID userId) {
        return TokenResponse.builder()
                .accessToken(createAccessToken(userId))
                .tokenType("Bearer")
                .build();
    }
}