package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;

public record UpdateItemRequest(
        // 생략 가능 — 순서는 이 필드 대신 reorderItems(방문 항목 순서 일괄 변경) API로 반영하는 게 원칙
        @Min(0) Integer orderIndex,
        LocalTime arrivalTime,
        Integer durationMin,
        @Pattern(regexp = "walk|transit|taxi", message = "travelMode은 walk, transit, taxi 중 하나여야 합니다.")
        String travelMode,
        Integer travelTimeMin,
        String memo
) {}
