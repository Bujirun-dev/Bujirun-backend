package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class GroupItineraryGenerateResponse {
    private UUID voteSessionId;
    private ItineraryGenerateResponse plans;
    private GroupPreferenceSummary groupSummary; // 추가: 그룹원 취향 집계 결과

    // AI 생성을 실제로 수행한 요청의 시작/종료 시각. 그룹당 1회만 생성되는 값이므로,
    // 모든 멤버는 각자의 화면 상태가 아니라 이 값을 시작/종료 시각의 단일 source of truth로
    // 삼아야 한다 (2026-09-05: 프론트 URL 파라미터로만 전달되다 초대 링크에서 유실되어
    // 팀원 화면에 다른 시각이 표시되던 문제).
    private LocalTime startTime;
    private LocalTime endTime;
}