package com.bujirun.bujirun.domain.itinerary.optimize.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
public class ItineraryOptimizeRequest {
    private String optimizationType; // WALK_MIN / COST_SAVE / TRANSFER_MIN / TIME_SHORT
    private LocalTime startTime;     // null이면 기존 첫 스팟 도착시각 또는 09:00 기본값
}