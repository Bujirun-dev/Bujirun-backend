package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ItineraryGenerateResponse {

    private PlanOption planA; // 취향 집중형
    private PlanOption planB; // 균형 최적형
    private PlanOption planC; // 자유 편집형

    @Getter
    @Builder
    public static class PlanOption {
        private String type;        // "A" | "B" | "C"
        private String label;       // "취향 집중형" 등
        private String description; // 한 줄 설명
        private List<DayPlan> days;
    }

    @Getter
    @Builder
    public static class DayPlan {
        private int day;
        private List<SpotInfo> spots;
    }
}