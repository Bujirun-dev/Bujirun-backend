package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateItineraryRequest(
        String title,
        LocalDate startAt,
        LocalTime startTime,
        LocalDate endAt,
        LocalTime endTime,
        @Pattern(regexp = "draft|confirmed", message = "status는 draft 또는 confirmed여야 합니다.")
        String status
) {}
