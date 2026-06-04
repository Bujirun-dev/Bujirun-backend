package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;

public record UpdateItemRequest(
        @Min(0) int orderIndex,
        LocalTime arrivalTime,
        Integer durationMin,
        @Pattern(regexp = "walk|transit|taxi", message = "travelMode은 walk, transit, taxi 중 하나여야 합니다.")
        String travelMode,
        Integer travelTimeMin,
        String memo
) {}
