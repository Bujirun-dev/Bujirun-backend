package com.bujirun.bujirun.domain.itinerary.generate.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class GroupItineraryRequest {

    @NotNull
    private LocalDate startDate;

    private LocalTime startTime;

    @NotNull
    private LocalDate endDate;

    private LocalTime endTime;
    
    private String optimizationType;
}