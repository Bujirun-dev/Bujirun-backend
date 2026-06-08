package com.bujirun.bujirun.domain.itinerary.generate.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class SwipeRequest {

    @NotEmpty
    private List<SwipeItem> swipes; // 스와이프 결과 목록

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String optimizationType; // "WALK_MIN" | "COST_SAVE" | "TIME_SHORT"

    @Getter
    public static class SwipeItem {
        private String contentId; // 관광지 content_id
        private boolean liked;    // true = 좋아요(→), false = 싫어요(←)
    }
}