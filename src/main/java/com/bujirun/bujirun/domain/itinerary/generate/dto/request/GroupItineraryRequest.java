package com.bujirun.bujirun.domain.itinerary.generate.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
public class GroupItineraryRequest {

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
    private LocalDate startDate;

    private LocalTime startTime;

    @NotNull
    @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
    private LocalDate endDate;

    private LocalTime endTime;
    
    private String optimizationType;
}