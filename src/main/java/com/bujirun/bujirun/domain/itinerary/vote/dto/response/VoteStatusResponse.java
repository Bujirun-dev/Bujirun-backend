package com.bujirun.bujirun.domain.itinerary.vote.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class VoteStatusResponse {
    private UUID sessionId;
    private String status;
    private Map<String, Long> voteCounts;
    private int totalVotes;
}
