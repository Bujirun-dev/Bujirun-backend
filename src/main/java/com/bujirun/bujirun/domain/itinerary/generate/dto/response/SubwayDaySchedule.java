package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import java.util.List;

public record SubwayDaySchedule(
        List<SubwayDeparture> up,   // 상행 방면 배차 시각표
        List<SubwayDeparture> down  // 하행 방면 배차 시각표
) {}
