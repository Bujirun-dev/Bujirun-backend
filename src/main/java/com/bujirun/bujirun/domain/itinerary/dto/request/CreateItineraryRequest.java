package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateItineraryRequest(
        @NotNull UUID userId,
        @Pattern(regexp = "[ABC]", message = "planType은 A, B, C 중 하나여야 합니다.")
        String planType,
        String title
) {}
