package com.bujirun.bujirun.domain.itinerary.optimize.dto.response;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class ItineraryOptimizeResponse {
    private List<OptimizedSpot> spots;
    private List<TransitRouteResponse> routes;
    private String reason; // "N번 관광지가 마감임박이라 순서를 앞당겼어요" 같은 한 줄 설명

    @Getter
    @Builder
    public static class OptimizedSpot {
        private String contentId;
        private String name;
        private int order;
        private LocalTime arrivalTime;
    }
}