package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateItineraryRequest(
        String title,
        @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
        LocalDate startAt,
        LocalTime startTime,
        @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
        LocalDate endAt,
        LocalTime endTime,
        @Pattern(regexp = "draft|confirmed", message = "status는 draft 또는 confirmed여야 합니다.")
        String status
) {}
