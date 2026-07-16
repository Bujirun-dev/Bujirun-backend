package com.bujirun.bujirun.domain.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class KakaoUnlinkService {

    @Value("${kakao.admin-key}")
    private String adminKey;

    public void unlink(String providerId) {
        try {
            WebClient.create("https://kapi.kakao.com")
                    .post()
                    .uri("/v1/user/unlink")
                    .header("Authorization", "KakaoAK " + adminKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("target_id_type=user_id&target_id=" + providerId)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();
        } catch (Exception e) {
            // 카카오 연결 해제 실패해도 탈퇴는 계속 진행
            log.warn("카카오 연결 해제 실패. providerId={}", providerId, e);
        }
    }
}