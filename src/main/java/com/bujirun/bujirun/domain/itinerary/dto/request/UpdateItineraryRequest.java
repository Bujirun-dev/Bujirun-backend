package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Pattern;

public record UpdateItineraryRequest(
        String title,
        @Pattern(regexp = "draft|confirmed", message = "status는 draft 또는 confirmed여야 합니다.")
        String status
) {}
