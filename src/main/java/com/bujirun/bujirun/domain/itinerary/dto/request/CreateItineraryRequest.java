package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateItineraryRequest(
        @Pattern(regexp = "[ABC]", message = "planType은 A, B, C 중 하나여야 합니다.")
        String planType,
        String title,
        @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
        LocalDate startAt,
        LocalTime startTime,
        @FutureOrPresent(message = "지난 날짜로는 일정을 생성할 수 없습니다.")
        LocalDate endAt,
        LocalTime endTime,
        UUID groupId,
        UUID sessionId
) {}
