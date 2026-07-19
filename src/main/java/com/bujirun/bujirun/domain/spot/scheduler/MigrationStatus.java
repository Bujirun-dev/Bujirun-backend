package com.bujirun.bujirun.domain.spot.scheduler;

public enum MigrationStatus {
    IDLE,       // 실행된 적 없음
    RUNNING,    // 진행 중
    COMPLETED,  // 완료
    FAILED      // 실패
}
