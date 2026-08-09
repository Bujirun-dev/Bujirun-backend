package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class GroupItineraryGenerateResponse {
    private UUID voteSessionId;
    private ItineraryGenerateResponse plans;
    private GroupPreferenceSummary groupSummary; // 추가: 그룹원 취향 집계 결과
}