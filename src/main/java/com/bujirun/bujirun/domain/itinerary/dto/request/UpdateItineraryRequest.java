package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

// 날짜에 @FutureOrPresent를 걸지 않는다(생성 DTO인 CreateItineraryRequest에만 유지).
// 어제 시작해 내일 끝나는 "진행 중인 여행"은 startAt이 과거라서, 이름만 바꾸는 요청도
// "지난 날짜로는 일정을 생성할 수 없습니다." 400을 맞고 아무것도 수정할 수 없었다.
// 과거 날짜로 새로 만드는 것을 막는 의도는 생성 DTO에서 그대로 지켜진다.
public record UpdateItineraryRequest(
        String title,
        LocalDate startAt,
        LocalTime startTime,
        LocalDate endAt,
        LocalTime endTime,
        String accommodationName,
        String accommodationAddress,
        Double accommodationLat,
        Double accommodationLng,
        @Pattern(regexp = "draft|confirmed", message = "status는 draft 또는 confirmed여야 합니다.")
        String status
) {}
