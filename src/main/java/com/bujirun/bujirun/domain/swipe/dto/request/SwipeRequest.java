package com.bujirun.bujirun.domain.swipe.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeRequest {

    @NotEmpty
    private List<SwipeItem> swipes; // 스와이프 결과 목록

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String optimizationType; // "WALK_MIN" | "COST_SAVE" | "TIME_SHORT" | "TRANSFER_MIN"

    private int activityHours; // 하루 활동 가능 시간 (2-8)

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SwipeItem {
        private String contentId; // 관광지 content_id
        private boolean liked;    // true = 좋아요(→), false = 싫어요(←)
    }
}