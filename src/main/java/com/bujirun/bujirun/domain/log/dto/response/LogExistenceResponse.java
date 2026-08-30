package com.bujirun.bujirun.domain.log.dto.response;

import java.util.UUID;

public record LogExistenceResponse(
        UUID itineraryId,
        // 2026-08-30부터 종료된 일정은 이 API 호출 시점에 로그가 자동 생성되므로, 사용자가 영수증을
        // 실제로 "발행"했는지와 무관하게 일정 종료 후엔 계속 true다. 영수증 팝업을 다시 띄워야
        // 하는지 판단할 땐 이 필드가 아니라 receiptCompleted를 써야 한다.
        boolean hasLog,
        UUID logId,
        // 사용자가 이 일정의 영수증 발행 팝업을 "다시 묻지 않음" 처리했는지 여부.
        // true면 receiptCompleted가 false여도 프론트는 팝업을 띄우지 않는다.
        boolean promptDismissed,
        // 자동 생성된 빈 로그가 아니라 mood를 채워 실제로 영수증 발행까지 마쳤는지 여부.
        // 영수증 팝업 자동 노출 여부는 hasLog가 아니라 이 필드로 판단해야 한다(2026-08-30).
        boolean receiptCompleted
) {}
