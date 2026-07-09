package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class GroupItineraryGenerateResponse {
    private UUID voteSessionId;
    private ItineraryGenerateResponse plans;
}