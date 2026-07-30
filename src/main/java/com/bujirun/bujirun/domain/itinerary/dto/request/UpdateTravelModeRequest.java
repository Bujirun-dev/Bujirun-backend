package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTravelModeRequest(
        @NotBlank
        @Pattern(regexp = "walk|transit|taxi", message = "travelMode은 walk, transit, taxi 중 하나여야 합니다.")
        String travelMode
) {}
