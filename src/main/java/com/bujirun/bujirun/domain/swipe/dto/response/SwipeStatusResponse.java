package com.bujirun.bujirun.domain.swipe.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SwipeStatusResponse {
    private long doneCount;
    private long totalCount;
    private boolean allDone;
    // 이 그룹의 일정 생성(투표 세션)이 이미 시작됐는지. 방장이 전원 완료를 기다리지 않고 넘어가면
    // allDone은 계속 false라서, 대기 화면의 팀원은 이 값으로 다음 단계로 따라간다.
    private boolean generationStarted;
}
