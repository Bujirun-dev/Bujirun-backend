package com.bujirun.bujirun.domain.auth.scheduler;

import com.bujirun.bujirun.domain.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 후 30일(UserService.PURGE_GRACE_PERIOD_DAYS)이 지난 계정의 개인정보
 * (방문 인증 기록/GPS, 인증 사진, 여행 기록)를 완전히 삭제하는 배치.
 * 30일 이내엔 카카오 재로그인으로 계정이 복구될 수 있어 그 전엔 보존한다.
 */
@Component
@RequiredArgsConstructor
public class AccountPurgeScheduler {

    private final UserService userService;

    // 매일 새벽 4시 실행
    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpiredAccounts() {
        userService.purgeExpiredAccountData();
    }
}
