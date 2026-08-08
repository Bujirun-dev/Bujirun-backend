package com.bujirun.bujirun.domain.group.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class GroupPreferenceSummary {
    private int participantCount; // 그룹 스와이프를 완료한 참여자 수
    private Map<String, Long> categoryCounts; // 카테고리별 좋아요 합계, 예: {"바다": 4, "사진명소": 3}
}
