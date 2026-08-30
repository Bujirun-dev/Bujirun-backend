package com.bujirun.bujirun.domain.log.dto.response;

import java.util.UUID;

public record LogExistenceResponse(
        UUID itineraryId,
        // 2026-08-30부터 종료된 일정은 이 API 호출 시점에 로그가 자동 생성되므로, 사용자가 영수증을
        // 실제로 "발행"했는지와 무관하게 일정 종료 후엔 계속 true다. mood/theme/공개여부까지 채웠는지는
        // 이 필드로 구분할 수 없다 — 그건 프론트가 별도로 판단해야 한다.
        boolean hasLog,
        UUID logId,
        // 사용자가 이 일정의 영수증 발행 팝업을 "다시 묻지 않음" 처리했는지 여부.
        // true면 hasLog가 false여도 프론트는 팝업을 띄우지 않는다.
        boolean promptDismissed
) {}
