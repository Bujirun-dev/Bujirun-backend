package com.bujirun.bujirun.global.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.UUID;

// Redis에 Refresh Token 저장/조회/삭제 담당
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "refresh:"; // Redis 키 prefix

    // Refresh Token 저장 (2주 후 자동 만료)
    public void save(UUID userId, String refreshToken, long expirationMs) {
        redisTemplate.opsForValue().set(
                PREFIX + userId,
                refreshToken,
                Duration.ofMillis(expirationMs)
        );
    }

    // Refresh Token 조회
    public String find(UUID userId) {
        return redisTemplate.opsForValue().get(PREFIX + userId);
    }

    // Refresh Token 삭제 (로그아웃 시 사용)
    public void delete(UUID userId) {
        redisTemplate.delete(PREFIX + userId);
    }
}