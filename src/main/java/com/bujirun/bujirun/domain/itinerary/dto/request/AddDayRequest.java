package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddDayRequest(
        @NotNull @Min(1) Integer dayNumber,
        LocalDate date
) {}
