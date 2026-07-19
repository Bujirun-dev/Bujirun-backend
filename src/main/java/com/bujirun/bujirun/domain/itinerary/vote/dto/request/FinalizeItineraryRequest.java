package com.bujirun.bujirun.domain.itinerary.vote.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
public class FinalizeItineraryRequest {

    @NotNull
    private Boolean freePass; // true면 투표 결과 무시하고 selectedPlan으로 즉시 확정

    private String selectedPlan; // 프리패스이거나 동률일 때 필수 (A/B/C)

    @NotNull
    private String title;

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 확정할 수 없습니다.")
    private LocalDate startDate;

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 확정할 수 없습니다.")
    private LocalDate endDate;

    // C안(직접 편집) 또는 최종 편집 결과. null이면 세션에 저장된 plansJson에서 selectedPlan 그대로 사용
    private List<DayInput> days;

    private UUID requesterId; // 컨트롤러에서 인증된 userId로 채워짐

    public void setRequesterId(UUID requesterId) {
        this.requesterId = requesterId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayInput {
        private int day;
        private List<String> spotContentIds;
    }
}