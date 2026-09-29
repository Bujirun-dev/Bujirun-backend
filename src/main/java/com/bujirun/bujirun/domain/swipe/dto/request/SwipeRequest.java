package com.bujirun.bujirun.domain.swipe.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeRequest {

    private List<SwipeItem> swipes; // 스와이프 결과 목록

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
    private LocalDate startDate;

    private LocalTime startTime;

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
    private LocalDate endDate;

    private LocalTime endTime;

    private String optimizationType; // "WALK_MIN" | "COST_SAVE" | "TIME_SHORT" | "TRANSFER_MIN"

    private Integer activityHours; // int -> Integer: 미입력(null)과 0을 구분하기 위함

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SwipeItem {
        private String contentId; // 관광지 content_id
        private boolean liked;    // true = 좋아요(→), false = 싫어요(←)
    }
}