package com.bujirun.bujirun.domain.itinerary.exception;

import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryDayResponse;
import lombok.Getter;

// day의 version이 요청의 expectedVersion과 다를 때(그 사이 다른 요청이 먼저 반영됐을 때)
// 던진다. 서버가 이미 조회해둔 최신 day 상태를 그대로 실어보내, 프론트가 추가 조회 없이
// 바로 reconcile(로컬 Yjs를 이 상태로 덮어쓰기)할 수 있게 한다.
@Getter
public class DayVersionConflictException extends RuntimeException {

    private final ItineraryDayResponse currentDay;

    public DayVersionConflictException(ItineraryDayResponse currentDay) {
        super("다른 편집이 먼저 반영되어 최신 상태와 버전이 달라요. 최신 상태를 반영해주세요.");
        this.currentDay = currentDay;
    }
}
