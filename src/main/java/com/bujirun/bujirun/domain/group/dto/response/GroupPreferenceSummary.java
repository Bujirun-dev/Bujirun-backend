package com.bujirun.bujirun.domain.group.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class GroupPreferenceSummary {
    private int participantCount; // 그룹 스와이프를 완료한 참여자 수
    private Map<String, Long> categoryCounts; // 카테고리별 좋아요 합계, 예: {"바다": 4, "사진명소": 3}

    // 참여자별 selectivity(1 - 좋아요수/전체스와이프수) 가중치를 반영한 카테고리별 점수
    // 모든 곳에 좋아요를 누른 참여자보다, 취향을 뚜렷이 구별한 참여자의 좋아요에 더 큰 비중을 둔다
    private Map<String, Double> categoryScore;

    // categoryScore 1위-2위의 상대 격차가 임계치 미만이면 true (특정 카테고리로 쏠리지 않은 편중 없는 취향)
    private boolean isUniformPreference;
}
