package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record UpdateItineraryRequest(
        String title,
        LocalDate startAt,
        LocalDate endAt,
        @Pattern(regexp = "draft|confirmed", message = "status는 draft 또는 confirmed여야 합니다.")
        String status
) {}
