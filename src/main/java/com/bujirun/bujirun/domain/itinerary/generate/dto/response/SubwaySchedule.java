package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

/**
 * ODsay searchSubwaySchedule 응답 — 부산교통공사가 ODsay에 등록해둔 배차 시각표.
 * GPS 기반 실시간 도착정보가 아니라 "시각표 기준 예정" 데이터이므로 응답/필드명에 "실시간" 표기를 쓰지 않는다.
 */
public record SubwaySchedule(
        String stationName,
        int stationId,
        SubwayDaySchedule weekdaySchedule,
        SubwayDaySchedule saturdaySchedule,
        SubwayDaySchedule holidaySchedule
) {}
